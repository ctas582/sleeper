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

import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import sleeper.configuration.properties.S3InstanceProperties;
import sleeper.core.properties.instance.InstanceProperties;

import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.CONFIG_BUCKET;
import static sleeper.core.properties.testutils.InstancePropertiesTestHelper.createTestInstanceProperties;
import static sleeper.localstack.test.SleeperLocalStackClients.S3_CLIENT;

/**
 * Gives a test a Sleeper instance for the API to serve, held in LocalStack.
 */
public class SleeperInstanceForTest {

    private SleeperInstanceForTest() {
    }

    /**
     * Creates the bucket the instance configuration is held in. Does nothing if it is already there, so that tests
     * sharing the container do not interfere with one another.
     */
    public static void createConfigBucket() {
        try {
            S3_CLIENT.createBucket(builder -> builder.bucket(LocalStackTestResource.CONFIG_BUCKET));
        } catch (BucketAlreadyOwnedByYouException e) {
            // Another test created it already.
        }
    }

    /**
     * Creates the configuration of an instance, with everything Sleeper needs to consider it valid. A test sets what
     * it cares about on top of this, because the API refuses configuration that does not validate.
     *
     * @return the configuration
     */
    public static InstanceProperties instance() {
        InstanceProperties properties = createTestInstanceProperties();
        properties.set(CONFIG_BUCKET, LocalStackTestResource.CONFIG_BUCKET);
        return properties;
    }

    /**
     * Makes the given configuration the one the API will read.
     *
     * @param properties the configuration
     */
    public static void saveProperties(InstanceProperties properties) {
        S3InstanceProperties.saveToS3(S3_CLIENT, properties);
    }

}
