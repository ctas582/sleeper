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
package sleeper.api.rest.security;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import io.quarkus.amazon.lambda.http.LambdaIdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;

import sleeper.api.auth.PrincipalId;
import sleeper.api.auth.iam.IamPrincipalMapper;

import java.util.Optional;

/**
 * Identifies requests that were signed using an AWS IAM role.
 */
@ApplicationScoped
public class SleeperLambdaIdentityProvider implements LambdaIdentityProvider {

    @Override
    public SecurityIdentity authenticate(APIGatewayV2HTTPEvent event) {
        return callerArn(event)
                .flatMap(IamPrincipalMapper::principalFor)
                .map(SleeperLambdaIdentityProvider::identityFor)
                .orElse(null);
    }

    private static Optional<String> callerArn(APIGatewayV2HTTPEvent event) {
        return Optional.ofNullable(event)
                .map(APIGatewayV2HTTPEvent::getRequestContext)
                .map(APIGatewayV2HTTPEvent.RequestContext::getAuthorizer)
                .map(APIGatewayV2HTTPEvent.RequestContext.Authorizer::getIam)
                .map(APIGatewayV2HTTPEvent.RequestContext.IAM::getUserArn);
    }

    private static SecurityIdentity identityFor(PrincipalId principal) {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(principal::toString)
                .addAttribute(SleeperIdentity.PRINCIPAL, principal)
                .build();
    }

}
