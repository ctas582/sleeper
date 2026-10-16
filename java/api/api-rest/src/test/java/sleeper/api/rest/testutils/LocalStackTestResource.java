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
package sleeper.api.rest.testutils;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.localstack.LocalStackContainer;

import sleeper.localstack.test.SleeperLocalStackContainer;

import java.util.Map;

/**
 * Points the API at a LocalStack container instead of AWS.
 */
public class LocalStackTestResource implements QuarkusTestResourceLifecycleManager {

    public static final String CONFIG_BUCKET = "sleeper-test-instance-config";

    @Override
    public Map<String, String> start() {
        LocalStackContainer container = SleeperLocalStackContainer.INSTANCE;
        return Map.of(
                "quarkus.s3.endpoint-override", container.getEndpoint().toString(),
                "quarkus.s3.aws.region", container.getRegion(),
                "quarkus.s3.aws.credentials.type", "static",
                "quarkus.s3.aws.credentials.static-provider.access-key-id", container.getAccessKey(),
                "quarkus.s3.aws.credentials.static-provider.secret-access-key", container.getSecretKey(),
                "quarkus.s3.path-style-access", "true",
                "sleeper.config.bucket", CONFIG_BUCKET);
    }

    @Override
    public void stop() {
        // The container is shared across the build and torn down with the JVM.
    }

}
