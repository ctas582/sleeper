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
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent.RequestContext;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent.RequestContext.Authorizer;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent.RequestContext.IAM;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import sleeper.api.auth.PrincipalId;

import static org.assertj.core.api.Assertions.assertThat;

class SleeperLambdaIdentityProviderTest {

    private final SleeperLambdaIdentityProvider provider = new SleeperLambdaIdentityProvider();

    @Test
    void shouldIdentifyTheRoleBehindTheCaller() {
        SecurityIdentity identity = provider.authenticate(
                eventCalledBy("arn:aws:sts::123456789012:assumed-role/Ingest/i-0abc123"));

        assertThat(SleeperIdentity.principalOf(identity))
                .contains(PrincipalId.forIamRole("arn:aws:iam::123456789012:role/Ingest"));
    }

    @Test
    void shouldIdentifyTheSameRoleForDifferentSessions() {
        SecurityIdentity first = provider.authenticate(
                eventCalledBy("arn:aws:sts::123456789012:assumed-role/Ingest/session-one"));
        SecurityIdentity second = provider.authenticate(
                eventCalledBy("arn:aws:sts::123456789012:assumed-role/Ingest/session-two"));

        assertThat(SleeperIdentity.principalOf(first))
                .isEqualTo(SleeperIdentity.principalOf(second));
    }

    @Test
    void shouldIdentifyACallerInAnotherPartition() {
        SecurityIdentity identity = provider.authenticate(
                eventCalledBy("arn:aws-iso:sts::123456789012:assumed-role/Ingest/session"));

        assertThat(SleeperIdentity.principalOf(identity))
                .contains(PrincipalId.forIamRole("arn:aws-iso:iam::123456789012:role/Ingest"));
    }

    @Test
    void shouldNotGrantAnyPermissionsWhenIdentifyingACaller() {
        SecurityIdentity identity = provider.authenticate(
                eventCalledBy("arn:aws:sts::123456789012:assumed-role/Ingest/session"));

        assertThat(SleeperIdentity.permissionsOf(identity).isEmpty()).isTrue();
    }

    @Test
    void shouldNotIdentifyARequestWithNoIamAuthorizer() {
        assertThat(provider.authenticate(eventWithNoAuthorizer())).isNull();
    }

    @Test
    void shouldNotIdentifyARequestWithNoRequestContext() {
        assertThat(provider.authenticate(APIGatewayV2HTTPEvent.builder().build())).isNull();
    }

    @Test
    void shouldNotIdentifyACallerWhoseArnCannotBeRead() {
        assertThat(provider.authenticate(eventCalledBy("not an arn"))).isNull();
    }

    @Test
    void shouldNotIdentifyARequestWithNoCallerArn() {
        assertThat(provider.authenticate(eventCalledBy(null))).isNull();
    }

    private static APIGatewayV2HTTPEvent eventCalledBy(String callerArn) {
        return APIGatewayV2HTTPEvent.builder()
                .withRequestContext(RequestContext.builder()
                        .withAuthorizer(Authorizer.builder()
                                .withIam(IAM.builder().withUserArn(callerArn).build())
                                .build())
                        .build())
                .build();
    }

    private static APIGatewayV2HTTPEvent eventWithNoAuthorizer() {
        return APIGatewayV2HTTPEvent.builder()
                .withRequestContext(RequestContext.builder().build())
                .build();
    }

}
