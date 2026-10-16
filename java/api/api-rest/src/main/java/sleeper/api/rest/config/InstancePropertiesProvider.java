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
package sleeper.api.rest.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.s3.S3Client;

import sleeper.configuration.properties.S3InstanceProperties;
import sleeper.core.properties.instance.InstanceProperties;

import java.util.Optional;

/**
 * Reads the configuration of the Sleeper instance this API is serving.
 */
@ApplicationScoped
public class InstancePropertiesProvider {

    private final S3Client s3Client;
    private final String configBucket;

    @Inject
    public InstancePropertiesProvider(
            S3Client s3Client,
            @ConfigProperty(name = "sleeper.config.bucket") Optional<String> configBucket,
            @ConfigProperty(name = "sleeper.instance.id") Optional<String> instanceId,
            @ConfigProperty(name = "sleeper.account.name") Optional<String> accountName) {
        this.s3Client = s3Client;
        this.configBucket = configBucket.orElseGet(() -> deriveConfigBucket(instanceId, accountName));
    }

    /**
     * Reads the configuration of the instance.
     *
     * @return the configuration
     */
    public InstanceProperties get() {
        return S3InstanceProperties.loadFromBucket(s3Client, configBucket);
    }

    /**
     * Works out which bucket holds the configuration when the bucket was not named directly.
     *
     * @param  instanceId               the ID of the instance
     * @param  accountName              the AWS account the instance is deployed in
     * @return                          the name of the bucket holding the configuration
     * @throws IllegalArgumentException if neither the bucket nor the instance was named
     */
    private static String deriveConfigBucket(Optional<String> instanceId, Optional<String> accountName) {
        return instanceId.flatMap(id -> accountName.map(
                account -> InstanceProperties.getConfigBucketFromAccountAndInstanceId(account, id)))
                .orElseThrow(() -> new IllegalArgumentException(
                        "The API does not know which Sleeper instance it is serving. Set sleeper.config.bucket, " +
                        "or set sleeper.instance.id and sleeper.account.name to run against a deployed instance."));
    }

}
