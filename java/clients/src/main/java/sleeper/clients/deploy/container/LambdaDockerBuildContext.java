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
package sleeper.clients.deploy.container;

import org.apache.commons.io.file.PathUtils;

import sleeper.clients.deploy.container.UploadDockerImages.CopyFile;
import sleeper.core.deploy.LambdaJar;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Places the code for a lambda in the directory its Docker image is built from.
 * <p>
 * A single jar is copied in as lambda.jar. A zip archive is unpacked into a directory named function, because it holds
 * its dependencies as jars nested inside it, which a Java classpath cannot load from inside the archive.
 */
public class LambdaDockerBuildContext {

    public static final String JAR_FILENAME = "lambda.jar";
    public static final String UNPACKED_ZIP_DIRECTORY = "function";

    private LambdaDockerBuildContext() {
    }

    /**
     * Places the code for a lambda in the directory its Docker image is built from.
     *
     * @param jar                 the definition of the lambda's code
     * @param artefact            the file holding the lambda's code
     * @param dockerfileDirectory the directory the Docker image is built from
     * @param copyFile            how to copy a single jar into place
     */
    public static void addLambdaCode(LambdaJar jar, Path artefact, Path dockerfileDirectory, CopyFile copyFile) {
        if (jar.isZipArchive()) {
            unpackReplacingContents(artefact, dockerfileDirectory.resolve(UNPACKED_ZIP_DIRECTORY));
        } else {
            copyFile.copyWrappingExceptions(artefact, dockerfileDirectory.resolve(JAR_FILENAME));
        }
    }

    private static void unpackReplacingContents(Path zipFile, Path directory) {
        try {
            if (Files.exists(directory)) {
                PathUtils.deleteDirectory(directory);
            }
            Files.createDirectories(directory);
            try (InputStream fileStream = Files.newInputStream(zipFile);
                    ZipInputStream zip = new ZipInputStream(fileStream)) {
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    unpackEntry(zip, entry, directory);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed unpacking " + zipFile + " to " + directory, e);
        }
    }

    private static void unpackEntry(ZipInputStream zip, ZipEntry entry, Path directory) throws IOException {
        Path target = directory.resolve(entry.getName()).normalize();
        if (!target.startsWith(directory.normalize())) {
            throw new IOException("Zip entry would be unpacked outside of the target directory: " + entry.getName());
        }
        if (entry.isDirectory()) {
            Files.createDirectories(target);
        } else {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(zip, target);
        }
    }
}
