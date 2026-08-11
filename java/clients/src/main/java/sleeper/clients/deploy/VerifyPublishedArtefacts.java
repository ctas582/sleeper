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
package sleeper.clients.deploy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ecr.model.EcrException;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import sleeper.clients.deploy.container.DockerImageConfiguration;
import sleeper.clients.deploy.container.StackDockerImage;
import sleeper.core.SleeperVersion;
import sleeper.core.deploy.LambdaJar;
import sleeper.core.properties.instance.InstanceProperties;
import sleeper.core.properties.model.SleeperArtefactsLocation;
import sleeper.core.properties.model.SleeperInternalCdkApp;

import java.util.ArrayList;
import java.util.List;

import static sleeper.core.properties.instance.CommonProperty.ARTEFACTS_PREFIX;
import static sleeper.core.properties.instance.CommonProperty.ECR_REPOSITORY_PREFIX;
import static sleeper.core.properties.instance.CommonProperty.JARS_BUCKET;

/**
 * Fail-fast pre-check used when artefacts mode is published. Verifies that every jar and Docker image that the
 * instance's CDK app will reference is already present in the configured S3 bucket and ECR repositories. Reports all
 * missing artefacts in a single error so the operator can fix their publish job in one pass.
 */
public class VerifyPublishedArtefacts {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerifyPublishedArtefacts.class);

    private final S3Client s3Client;
    private final EcrClient ecrClient;

    public VerifyPublishedArtefacts(S3Client s3Client, EcrClient ecrClient) {
        this.s3Client = s3Client;
        this.ecrClient = ecrClient;
    }

    public void verify(InstanceProperties instanceProperties, SleeperInternalCdkApp cdkApp) {
        String bucket = instanceProperties.get(JARS_BUCKET);
        String ecrPrefix = instanceProperties.get(ECR_REPOSITORY_PREFIX);
        if (bucket == null || bucket.isEmpty()) {
            throw new IllegalStateException("sleeper.jars.bucket must be set when sleeper.artefacts.mode=published");
        }
        if (ecrPrefix == null || ecrPrefix.isEmpty()) {
            throw new IllegalStateException("sleeper.ecr.repository.prefix must be set when sleeper.artefacts.mode=published");
        }
        // sleeper.version is set by CDK at synth time, which hasn't happened yet at this point in the deploy flow.
        // Use SleeperVersion (baked into the running client jar) so the pre-check reflects the version we're deploying.
        String version = SleeperVersion.getVersion();
        String artefactsPrefix = instanceProperties.get(ARTEFACTS_PREFIX);
        LOGGER.info("Verifying published artefacts for version {} in bucket {} (prefix {}) and ECR prefix {}",
                version, bucket, artefactsPrefix, ecrPrefix);

        List<String> missing = new ArrayList<>();
        verifyJars(instanceProperties, bucket, artefactsPrefix, version, missing);
        verifyImages(instanceProperties, cdkApp, version, missing);

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Published artefacts pre-check failed. "
                    + missing.size() + " required artefact(s) missing:\n  - "
                    + String.join("\n  - ", missing));
        }
        LOGGER.info("Published artefacts pre-check passed");
    }

    private void verifyJars(InstanceProperties instanceProperties, String bucket, String artefactsPrefix,
            String version, List<String> missing) {
        for (LambdaJar jar : LambdaJar.all()) {
            if (jar.isAlwaysDockerDeploy()) {
                continue;
            }
            String key = SleeperArtefactsLocation.applyPrefix(artefactsPrefix, jar.getFilename(version));
            try {
                s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            } catch (NoSuchKeyException e) {
                missing.add("s3://" + bucket + "/" + key);
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    missing.add("s3://" + bucket + "/" + key);
                } else {
                    throw e;
                }
            }
        }
    }

    private void verifyImages(InstanceProperties instanceProperties, SleeperInternalCdkApp cdkApp, String version,
            List<String> missing) {
        List<StackDockerImage> images = DockerImageConfiguration.getDefault().getNonBaseImagesToUpload(instanceProperties, cdkApp);
        for (StackDockerImage image : images) {
            String repositoryName = image.getLambdaJar()
                    .map(jar -> (String) jar.getEcrRepositoryName(instanceProperties))
                    .orElseGet(() -> ecrRepositoryNameForImage(instanceProperties, image.getImageName()));
            try {
                ecrClient.describeImages(DescribeImagesRequest.builder()
                        .repositoryName(repositoryName)
                        .imageIds(ImageIdentifier.builder().imageTag(version).build())
                        .build());
            } catch (EcrException e) {
                missing.add("ECR " + repositoryName + ":" + version + " (" + e.awsErrorDetails().errorCode() + ")");
            }
        }
    }

    private static String ecrRepositoryNameForImage(InstanceProperties instanceProperties, String imageName) {
        String artefactsPrefix = instanceProperties.get(ARTEFACTS_PREFIX);
        return instanceProperties.get(ECR_REPOSITORY_PREFIX) + "/"
                + SleeperArtefactsLocation.applyPrefix(artefactsPrefix, imageName);
    }
}
