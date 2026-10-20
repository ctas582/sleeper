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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.aws_apigatewayv2_authorizers.HttpIamAuthorizer;
import software.amazon.awscdk.aws_apigatewayv2_integrations.HttpLambdaIntegration;
import software.amazon.awscdk.services.apigatewayv2.AddRoutesOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.HttpMethod;
import software.amazon.awscdk.services.apigatewayv2.HttpRoute;
import software.amazon.awscdk.services.lambda.IFunction;
import software.constructs.Construct;

import sleeper.cdk.SleeperInstanceProps;
import sleeper.cdk.lambda.SleeperLambdaCode;
import sleeper.cdk.stack.core.LoggingStack.LogGroupRef;
import sleeper.core.deploy.LambdaHandler;
import sleeper.core.properties.instance.InstanceProperties;
import sleeper.core.util.EnvironmentUtils;

import java.util.List;

import static sleeper.core.properties.instance.ApiProperty.API_AUTH_IAM_ENABLED;
import static sleeper.core.properties.instance.ApiProperty.API_LAMBDA_MEMORY_IN_MB;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_IAM_URL;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_URL;

/**
 * The REST API for a Sleeper instance, and the UI it serves.
 */
@SuppressFBWarnings("MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR")
public class ApiStack extends NestedStack {

    // API Gateway stops waiting for a response after 30 seconds.
    private static final Duration TIMEOUT = Duration.seconds(29);

    public ApiStack(Construct scope, String id, SleeperInstanceProps props, SleeperCoreStacks coreStacks) {
        super(scope, id);
        InstanceProperties instanceProperties = props.getInstanceProperties();
        SleeperLambdaCode lambdaCode = props.getArtefacts().lambdaCodeAtScope(this);
        String instanceId = instanceProperties.cleanInstanceId();
        String functionName = String.join("-", "sleeper", instanceId, "api-handler");

        IFunction lambda = lambdaCode.buildFunction(LambdaHandler.API_HANDLER, "ApiHandlerLambda", builder -> builder
                .functionName(functionName)
                .description("Serves the Sleeper REST API")
                .environment(EnvironmentUtils.createDefaultEnvironment(instanceProperties))
                .memorySize(instanceProperties.getInt(API_LAMBDA_MEMORY_IN_MB))
                .logGroup(coreStacks.getLogGroup(LogGroupRef.API_HANDLER))
                .timeout(TIMEOUT));

        coreStacks.grantReadInstanceConfig(lambda);

        HttpApi tokenApi = HttpApi.Builder.create(this, "Api")
                .description("Sleeper API, for clients authenticating with a token")
                .apiName(functionName)
                .build();
        addProxyRoutes(tokenApi, lambda, instanceId);
        instanceProperties.set(REST_API_URL, tokenApi.getApiEndpoint());
        new CfnOutput(this, "ApiUrl", CfnOutputProps.builder()
                .value(tokenApi.getApiEndpoint())
                .build());

        if (instanceProperties.getBoolean(API_AUTH_IAM_ENABLED)) {
            HttpApi iamApi = HttpApi.Builder.create(this, "IamApi")
                    .description("Sleeper API, for clients authenticating with AWS credentials")
                    .apiName(functionName + "-iam")
                    .defaultAuthorizer(new HttpIamAuthorizer())
                    .build();
            addProxyRoutes(iamApi, lambda, instanceId + "-iam")
                    .forEach(route -> route.grantInvoke(coreStacks.getAdminPolicyForGrants()));
            instanceProperties.set(REST_API_IAM_URL, iamApi.getApiEndpoint());
            new CfnOutput(this, "ApiIamUrl", CfnOutputProps.builder()
                    .value(iamApi.getApiEndpoint())
                    .build());
        }
    }

    private List<HttpRoute> addProxyRoutes(HttpApi api, IFunction lambda, String integrationName) {
        return api.addRoutes(AddRoutesOptions.builder()
                .path("/{proxy+}")
                .methods(List.of(HttpMethod.ANY))
                .integration(HttpLambdaIntegration.Builder.create(integrationName, lambda).build())
                .build());
    }

}
