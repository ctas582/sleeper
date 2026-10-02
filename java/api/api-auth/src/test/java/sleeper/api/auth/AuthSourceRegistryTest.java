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
package sleeper.api.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import sleeper.core.properties.instance.InstanceProperties;
import sleeper.core.properties.model.OidcIssuerConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_COGNITO_ENABLED;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_IAM_ENABLED;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_OIDC_ISSUERS;

public class AuthSourceRegistryTest {

    private static final String ISSUER = "https://idp.internal/realms/sleeper";

    private final InstanceProperties properties = new InstanceProperties();

    @Nested
    @DisplayName("Report whether a client can authenticate at all")
    class ReportWhetherClientCanAuthenticate {

        @Test
        void shouldHaveNoWayToAuthenticateWhenNothingIsConfigured() {
            properties.set(API_AUTH_IAM_ENABLED, "false");

            assertThat(registry().isConfigured()).isFalse();
        }

        @Test
        void shouldAuthenticateWithAnOidcIssuer() {
            properties.set(API_AUTH_IAM_ENABLED, "false");
            setIssuers();

            assertThat(registry().isConfigured()).isTrue();
        }

        @Test
        void shouldAuthenticateWithIamAlone() {
            properties.set(API_AUTH_IAM_ENABLED, "true");

            assertThat(registry().isConfigured()).isTrue();
        }

        @Test
        void shouldAuthenticateWithCognitoAlone() {
            properties.set(API_AUTH_IAM_ENABLED, "false");
            properties.set(API_AUTH_COGNITO_ENABLED, "true");

            assertThat(registry().isConfigured()).isTrue();
        }

        @Test
        void shouldAuthenticateWithIamByDefault() {
            assertThat(registry().isConfigured()).isTrue();
            assertThat(registry().isIamEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("Find the issuer to verify a token with")
    class FindIssuerToVerifyWith {

        @Test
        void shouldFindAConfiguredIssuer() {
            setIssuers();

            assertThat(registry().forIssuer(ISSUER))
                    .map(OidcIssuerConfig::name)
                    .contains("corp");
        }

        @Test
        void shouldNotFindAnIssuerThatIsNotConfigured() {
            setIssuers();

            assertThat(registry().forIssuer("https://somewhere.else/realms/sleeper")).isEmpty();
        }

        @Test
        void shouldNotFindAnIssuerWhenNoneAreConfigured() {
            assertThat(registry().forIssuer(ISSUER)).isEmpty();
        }

        @Test
        void shouldNotFindAnIssuerForAMissingClaim() {
            setIssuers();

            assertThat(registry().forIssuer(null)).isEmpty();
        }

        @Test
        void shouldKeepIssuersInTheOrderTheyWereConfigured() {
            properties.set(API_AUTH_OIDC_ISSUERS, """
                    [{"name": "first", "issuer": "https://first.internal", "audiences": ["sleeper-api"],
                      "jwksUri": "https://first.internal/certs"},
                     {"name": "second", "issuer": "https://second.internal", "audiences": ["sleeper-api"],
                      "jwksUri": "https://second.internal/certs"}]
                    """);

            assertThat(registry().issuers())
                    .extracting(OidcIssuerConfig::name)
                    .containsExactly("first", "second");
        }
    }

    private AuthSourceRegistry registry() {
        return AuthSourceRegistry.from(properties);
    }

    private void setIssuers() {
        properties.set(API_AUTH_OIDC_ISSUERS, """
                [{"name": "corp", "issuer": "%s", "audiences": ["sleeper-api"],
                  "jwksUri": "https://idp.internal/certs"}]
                """.formatted(ISSUER));
    }

}
