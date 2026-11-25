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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sleeper.core.deploy.LambdaJar;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LambdaDockerBuildContextTest {

    @TempDir
    Path dir;
    Path dockerfileDirectory;

    @BeforeEach
    void setUp() throws Exception {
        dockerfileDirectory = Files.createDirectories(dir.resolve("docker"));
    }

    @Test
    void shouldCopySingleJar() throws Exception {
        // Given
        Path artefact = Files.writeString(dir.resolve("statestore.jar"), "jar-content");

        // When
        addLambdaCode(jar("statestore.jar"), artefact);

        // Then
        assertThat(filesUnder(dockerfileDirectory)).isEqualTo(Map.of(
                "lambda.jar", "jar-content"));
    }

    @Test
    void shouldUnpackZipArchive() throws Exception {
        // Given
        Path artefact = writeZip(dir.resolve("api.zip"), Map.of(
                "com/example/Handler.class", "handler-content",
                "lib/dependency.jar", "dependency-content"));

        // When
        addLambdaCode(jar("api.zip"), artefact);

        // Then
        assertThat(filesUnder(dockerfileDirectory)).isEqualTo(Map.of(
                "function/com/example/Handler.class", "handler-content",
                "function/lib/dependency.jar", "dependency-content"));
    }

    @Test
    void shouldRemoveFilesLeftByEarlierBuildWhenUnpackingZipArchive() throws Exception {
        // Given
        Files.createDirectories(dockerfileDirectory.resolve("function/lib"));
        Files.writeString(dockerfileDirectory.resolve("function/lib/old-dependency.jar"), "old-content");
        Path artefact = writeZip(dir.resolve("api.zip"), Map.of(
                "lib/new-dependency.jar", "new-content"));

        // When
        addLambdaCode(jar("api.zip"), artefact);

        // Then
        assertThat(filesUnder(dockerfileDirectory)).isEqualTo(Map.of(
                "function/lib/new-dependency.jar", "new-content"));
    }

    @Test
    void shouldRefuseToUnpackEntryOutsideFunctionDirectory() throws Exception {
        // Given
        Path artefact = writeZip(dir.resolve("api.zip"), Map.of(
                "../outside.txt", "content"));

        // When / Then
        assertThatThrownBy(() -> addLambdaCode(jar("api.zip"), artefact))
                .isInstanceOf(UncheckedIOException.class)
                .cause().hasMessageContaining("outside of the target directory");
        assertThat(dockerfileDirectory.resolve("outside.txt")).doesNotExist();
    }

    private void addLambdaCode(LambdaJar jar, Path artefact) {
        LambdaDockerBuildContext.addLambdaCode(jar, artefact, dockerfileDirectory,
                (source, target) -> Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING));
    }

    private static LambdaJar jar(String filename) {
        return LambdaJar.builder()
                .filenameFormat(filename)
                .imageName("test-lambda")
                .artifactId("test")
                .build();
    }

    private static Path writeZip(Path file, Map<String, String> entries) throws IOException {
        try (OutputStream fileStream = Files.newOutputStream(file);
                ZipOutputStream zip = new ZipOutputStream(fileStream)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }

    private static Map<String, String> filesUnder(Path directory) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(directory.relativize(path).toString(), Files.readString(path));
            }
        }
        return files;
    }
}
