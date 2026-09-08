/*
 * Copyright 2022-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sleeper.api.resources;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.schema.MessageType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

import sleeper.api.IngestMethod;
import sleeper.bulkimport.core.configuration.BulkImportPlatform;
import sleeper.bulkimport.core.job.BulkImportJob;
import sleeper.bulkimport.core.job.BulkImportJobSerDe;
import sleeper.configuration.properties.S3InstanceProperties;
import sleeper.configuration.properties.S3TableProperties;
import sleeper.configuration.table.index.DynamoDBTableIndex;
import sleeper.configuration.utils.S3Path;
import sleeper.core.properties.instance.InstanceProperties;
import sleeper.core.properties.model.IngestQueue;
import sleeper.core.properties.table.TableProperties;
import sleeper.core.properties.table.TablePropertiesProvider;
import sleeper.core.schema.Schema;
import sleeper.core.table.TableIndex;
import sleeper.core.table.TableStatus;
import sleeper.ingest.batcher.core.IngestBatcherSubmitRequest;
import sleeper.ingest.batcher.core.IngestBatcherSubmitRequestSerDe;
import sleeper.ingest.core.job.IngestJob;
import sleeper.ingest.core.job.IngestJobSerDe;
import sleeper.parquet.row.SchemaConverter;
import sleeper.parquet.utils.HadoopConfigurationProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static sleeper.api.ResourceUtils.loadPropertiesAndCheckStackEnabled;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.INGEST_BATCHER_SUBMIT_QUEUE_URL;

@Path("/api/ingest-file")
public class IngestFileResource {

    private final S3Client s3Client;
    private final DynamoDbClient dynamoDbClient;
    private final SqsClient sqsClient;
    private final String instanceId;
    private final String accountName;
    private final int maxFilesPerPath;

    @Inject
    public IngestFileResource(
            S3Client s3Client,
            DynamoDbClient dynamoDbClient,
            SqsClient sqsClient,
            @ConfigProperty(name = "sleeper.instance.id") String instanceId,
            @ConfigProperty(name = "sleeper.account.name") String accountName,
            @ConfigProperty(name = "sleeper.api.ingest-file.max-files-per-path", defaultValue = "250") int maxFilesPerPath) {
        this.s3Client = s3Client;
        this.dynamoDbClient = dynamoDbClient;
        this.sqsClient = sqsClient;
        this.instanceId = instanceId;
        this.accountName = accountName;
        this.maxFilesPerPath = maxFilesPerPath;
    }

    /**
     * Expands the requested S3 paths, discovering the Parquet files under any prefixes/directories. This
     * also verifies the paths can be read. The response flags which requested paths were empty (not found
     * or contained no Parquet files) so the user can be prompted to confirm which files to ingest.
     *
     * @param  request the paths to expand
     * @return         the files discovered under each path
     */
    @POST
    @Path("/expand")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ExpandResponse expand(ExpandRequest request) {
        if (request == null || request.paths() == null || request.paths().isEmpty()) {
            throw new WebApplicationException("Request must include at least one path", Response.Status.BAD_REQUEST);
        }

        List<ExpandedPath> paths = new ArrayList<>();
        List<String> missingPaths = new ArrayList<>();
        for (String requestedPath : request.paths()) {
            S3Path s3Path = S3Path.parse(requestedPath);

            List<ExpandedFile> files = s3Client.listObjectsV2Paginator(builder -> builder
                    .bucket(s3Path.bucket())
                    .prefix(s3Path.pathInBucket()))
                    .contents()
                    .stream()
                    .filter(s3Object -> s3Object.key().endsWith(".parquet"))
                    .limit(maxFilesPerPath + 1L)
                    .map(s3Object -> new ExpandedFile(s3Path.bucket() + "/" + s3Object.key(), s3Object.size()))
                    .collect(Collectors.toList());

            if (files.size() > maxFilesPerPath) {
                paths.add(new ExpandedPath(requestedPath, true, false, true, List.of()));
                continue;
            }

            if (files.isEmpty()) {
                missingPaths.add(requestedPath);
                paths.add(new ExpandedPath(requestedPath, false, true, false, List.of()));
                continue;
            }

            boolean isPrefix = !(files.size() == 1 && files.get(0).file().equals(stripScheme(requestedPath)));
            paths.add(new ExpandedPath(requestedPath, isPrefix, false, false, files));
        }

        return new ExpandResponse(paths, missingPaths, maxFilesPerPath);
    }

    public record ExpandRequest(List<String> paths) {}
    public record ExpandedFile(String file, long fileSizeBytes) {}
    public record ExpandedPath(String requestedPath, boolean prefix, boolean empty, boolean tooMany, List<ExpandedFile> files) {}
    public record ExpandResponse(List<ExpandedPath> paths, List<String> missingPaths, int maxFilesPerPath) {}

    /**
     * Checks that the confirmed files can be ingested. All files must share the same schema, and the
     * response lists the tables whose schema is compatible with the files.
     *
     * @param  request the confirmed files to inspect
     * @return         whether the files are compatible, and the tables they can be ingested into
     */
    @POST
    @Path("/inspect")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public InspectResponse inspect(InspectRequest request) {
        if (request == null || request.files() == null || request.files().isEmpty()) {
            throw new WebApplicationException("Request must include at least one file", Response.Status.BAD_REQUEST);
        }

        InstanceProperties instanceProperties = S3InstanceProperties.loadGivenAccountAndInstanceId(s3Client, accountName, instanceId);
        Configuration conf = HadoopConfigurationProvider.getConfigurationForClient(instanceProperties);

        List<MessageType> fileSchemas = request.files().stream()
                .map(file -> readParquetSchema(file, conf))
                .toList();

        TablePropertiesProvider tablePropertiesProvider = S3TableProperties.createProvider(instanceProperties, s3Client, dynamoDbClient);
        TableIndex tableIndex = new DynamoDBTableIndex(instanceProperties, dynamoDbClient);
        List<CompatibleTable> tables = new ArrayList<>();
        for (TableStatus table : tableIndex.streamAllTables().toList()) {
            TableProperties tableProperties = tablePropertiesProvider.getById(table.getTableUniqueId());
            Schema tableSchema = tableProperties.getSchema();
            if (fileSchemas.stream().allMatch(fileSchema -> SchemaConverter.isFileSchemaCompatibleWithTable(fileSchema, tableSchema))) {
                tables.add(new CompatibleTable(table.getTableUniqueId(), table.getTableName()));
            }
        }
        return new InspectResponse(fileSchemas.get(0).toString(), tables);
    }

    private MessageType readParquetSchema(String file, Configuration conf) {
        String hadoopPath = "s3a://" + file;
        try (ParquetFileReader reader = ParquetFileReader.open(HadoopInputFile.fromPath(new org.apache.hadoop.fs.Path(hadoopPath), conf))) {
            return reader.getFooter().getFileMetaData().getSchema();
        } catch (IOException e) {
            throw new WebApplicationException("Could not read Parquet file \"" + file + "\": " + e.getMessage(), Response.Status.BAD_REQUEST);
        }
    }

    public record InspectRequest(List<String> files) {}
    public record CompatibleTable(String tableId, String tableName) {}
    public record InspectResponse(String fileSchema, List<CompatibleTable> tables) {}

    /**
     * Submits the confirmed files for ingest into the selected tables using the chosen ingest method.
     *
     * @param  request the files, target tables and ingest method
     * @return         a summary of what was submitted
     */
    @POST
    @Path("/submit")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submit(SubmitRequest request) {
        if (request == null || request.files() == null || request.files().isEmpty()) {
            throw new WebApplicationException("Request must include at least one file", Response.Status.BAD_REQUEST);
        }
        if (request.tableIds() == null || request.tableIds().isEmpty()) {
            throw new WebApplicationException("Request must include at least one table", Response.Status.BAD_REQUEST);
        }
        IngestMethod method = readMethod(request.method());

        InstanceProperties instanceProperties = loadPropertiesAndCheckStackEnabled(s3Client, accountName, instanceId, method.getOptionalStack());
        TableIndex tableIndex = new DynamoDBTableIndex(instanceProperties, dynamoDbClient);

        List<SubmittedTable> submitted = new ArrayList<>();
        for (String tableId : request.tableIds()) {
            TableStatus table = tableIndex.getTableByUniqueId(tableId)
                    .orElseThrow(() -> new WebApplicationException("Table not found: " + tableId, Response.Status.BAD_REQUEST));
            String jobId = send(method, instanceProperties, table, request.files());
            submitted.add(new SubmittedTable(table.getTableName(), request.files().size(), jobId));
        }

        return Response.status(Response.Status.CREATED)
                .entity(new SubmitResponse(submitted, method.getWireName()))
                .build();
    }

    private static IngestMethod readMethod(String method) {
        if (method == null) {
            return IngestMethod.INGEST_BATCHER;
        }
        try {
            return IngestMethod.fromWireName(method);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        }
    }

    /**
     * Sends the files to the queue for the chosen ingest method.
     *
     * @param  method             the ingest method
     * @param  instanceProperties the instance properties
     * @param  table              the table to ingest into
     * @param  files              the files to ingest
     * @return                    the id of the job that was created, or null when the ingest batcher will create the
     *                            job later
     */
    private String send(IngestMethod method, InstanceProperties instanceProperties, TableStatus table, List<String> files) {
        if (method == IngestMethod.INGEST_BATCHER) {
            // The batcher tracks files by table name, and assigns them a job id once it creates a job.
            IngestBatcherSubmitRequest submitRequest = new IngestBatcherSubmitRequest(table.getTableName(), files);
            sendMessage(instanceProperties.get(INGEST_BATCHER_SUBMIT_QUEUE_URL),
                    new IngestBatcherSubmitRequestSerDe().toJson(submitRequest));
            return null;
        }

        String jobId = UUID.randomUUID().toString();
        Optional<BulkImportPlatform> platform = method.getBulkImportPlatform();
        if (platform.isPresent()) {
            BulkImportJob job = BulkImportJob.builder()
                    .id(jobId)
                    .tableId(table.getTableUniqueId())
                    .files(files)
                    .build();
            sendMessage(platform.get().getBulkImportQueueUrl(instanceProperties), new BulkImportJobSerDe().toJson(job));
        } else {
            IngestJob job = IngestJob.builder()
                    .id(jobId)
                    .tableId(table.getTableUniqueId())
                    .files(files)
                    .build();
            sendMessage(IngestQueue.STANDARD_INGEST.getJobQueueUrl(instanceProperties), new IngestJobSerDe().toJson(job));
        }
        return jobId;
    }

    private void sendMessage(String queueUrl, String body) {
        sqsClient.sendMessage(send -> send.queueUrl(queueUrl).messageBody(body));
    }

    public record SubmitRequest(List<String> files, List<String> tableIds, String method) {}
    public record SubmittedTable(String tableName, int fileCount, String jobId) {}
    public record SubmitResponse(List<SubmittedTable> submitted, String method) {}

    private static String stripScheme(String path) {
        int schemeEnd = path.indexOf("//");
        return schemeEnd >= 0 ? path.substring(schemeEnd + 2) : path;
    }

}