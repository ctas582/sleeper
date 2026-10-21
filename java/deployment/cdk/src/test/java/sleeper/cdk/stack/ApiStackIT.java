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
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_COGNITO_ENABLED;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_IAM_ENABLED;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_COGNITO_CLIENT_ID;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_COGNITO_ISSUER;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_COGNITO_LOGIN_URL;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_IAM_URL;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_URL;
import static sleeper.core.properties.instance.CommonProperty.RETAIN_INFRA_AFTER_DESTROY;

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
    void shouldGenerateCloudFormationTemplateWithCognito() {
        // Given
        instanceProperties.set(API_AUTH_COGNITO_ENABLED, "true");
        SleeperCoreStacks core = SleeperCoreStacks.create(rootStack, instanceProps());

        // When
        ApiStack stack = new ApiStack(rootStack, "Api", instanceProps(), core);

        // Then
        Approvals.verify(printer.toJson(stack), new Options()
                .forFile().withName("api-cognito", ".json"));
    }

    @Test
    void shouldSaveCognitoUserPoolDetailsToInstanceProperties() {
        // Given
        instanceProperties.set(API_AUTH_COGNITO_ENABLED, "true");
        SleeperCoreStacks core = SleeperCoreStacks.create(rootStack, instanceProps());

        // When
        new ApiStack(rootStack, "Api", instanceProps(), core);

        // Then
        assertThat(instanceProperties.get(REST_API_COGNITO_ISSUER)).isNotNull();
        assertThat(instanceProperties.get(REST_API_COGNITO_CLIENT_ID)).isNotNull();
        assertThat(instanceProperties.get(REST_API_COGNITO_LOGIN_URL)).isNotNull();
    }

    @Test
    void shouldNotCreateCognitoUserPoolIfNotEnabled() {
        // Given
        SleeperCoreStacks core = SleeperCoreStacks.create(rootStack, instanceProps());

        // When
        new ApiStack(rootStack, "Api", instanceProps(), core);

        // Then
        assertThat(instanceProperties.get(REST_API_COGNITO_ISSUER)).isNull();
        assertThat(instanceProperties.get(REST_API_COGNITO_CLIENT_ID)).isNull();
        assertThat(instanceProperties.get(REST_API_COGNITO_LOGIN_URL)).isNull();
    }

    @Test
    void shouldDeleteCognitoUserPoolWhenInfraIsToBeDeleted() {
        instanceProperties.set(API_AUTH_COGNITO_ENABLED, "true");
        instanceProperties.set(RETAIN_INFRA_AFTER_DESTROY, "false");
        SleeperCoreStacks core = SleeperCoreStacks.create(rootStack, instanceProps());

        // When
        ApiStack stack = new ApiStack(rootStack, "Api", instanceProps(), core);

        // Then
        assertThat(userPoolIn(printer.toJson(stack)))
                .contains("\"DeletionPolicy\": \"Delete\"")
                .doesNotContain("\"DeletionPolicy\": \"Retain\"");
    }

    @Test
    void shouldKeepCognitoUserPoolWhenInfraIsToBeRetained() {
        // Given
        instanceProperties.set(API_AUTH_COGNITO_ENABLED, "true");
        instanceProperties.set(RETAIN_INFRA_AFTER_DESTROY, "true");
        SleeperCoreStacks core = SleeperCoreStacks.create(rootStack, instanceProps());

        // When
        ApiStack stack = new ApiStack(rootStack, "Api", instanceProps(), core);

        // Then
        assertThat(userPoolIn(printer.toJson(stack)))
                .contains("\"DeletionPolicy\": \"Retain\"");
    }

    private static String userPoolIn(String template) {
        int start = template.indexOf("\"AWS::Cognito::UserPool\"");
        assertThat(start).isNotNegative();
        int end = template.indexOf("\"Type\": \"AWS::", start + 1);
        return end < 0 ? template.substring(start) : template.substring(start, end);
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
