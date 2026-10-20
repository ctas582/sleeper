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
package sleeper.cdk.stack;

import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.junit.jupiter.api.Test;

import sleeper.cdk.testutil.SleeperStackTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_IAM_ENABLED;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_IAM_URL;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_URL;

public class ApiStackIT extends SleeperStackTestBase {

    @Test
    void shouldGenerateCloudFormationTemplate() {
        // Given
        SleeperCoreStacks core = SleeperCoreStacks.create(rootStack, instanceProps());

        // When
        ApiStack stack = new ApiStack(rootStack, "Api", instanceProps(), core);

        // Then
        Approvals.verify(printer.toJson(stack), new Options()
                .forFile().withName("api", ".json"));
    }

    @Test
    void shouldGenerateCloudFormationTemplateWithNoEndpointForAwsCredentials() {
        // Given
        instanceProperties.set(API_AUTH_IAM_ENABLED, "false");
        SleeperCoreStacks core = SleeperCoreStacks.create(rootStack, instanceProps());

        // When
        ApiStack stack = new ApiStack(rootStack, "Api", instanceProps(), core);

        // Then
        Approvals.verify(printer.toJson(stack), new Options()
                .forFile().withName("api-no-iam", ".json"));
    }

    @Test
    void shouldTellClientsWhereToReachTheApi() {
        // Given
        SleeperCoreStacks core = SleeperCoreStacks.create(rootStack, instanceProps());

        // When
        new ApiStack(rootStack, "Api", instanceProps(), core);

        // Then
        assertThat(instanceProperties.get(REST_API_URL)).isNotNull();
        assertThat(instanceProperties.get(REST_API_IAM_URL)).isNotNull();
    }

    @Test
    void shouldNotTellClientsOfAnEndpointForAwsCredentialsThatWasNotDeployed() {
        // Given
        instanceProperties.set(API_AUTH_IAM_ENABLED, "false");
        SleeperCoreStacks core = SleeperCoreStacks.create(rootStack, instanceProps());

        // When
        new ApiStack(rootStack, "Api", instanceProps(), core);

        // Then
        assertThat(instanceProperties.get(REST_API_URL)).isNotNull();
        assertThat(instanceProperties.get(REST_API_IAM_URL)).isNull();
    }

}
