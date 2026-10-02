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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PrincipalIdTest {

    private static final String ROLE_ARN = "arn:aws:iam::123456789012:role/SleeperAdmin";
    private static final String ISSUER = "https://idp.internal/realms/sleeper";

    @Nested
    @DisplayName("Match a principal written in configuration against one built from a request")
    class MatchConfigurationAgainstRequest {

        @Test
        void shouldMatchARoleWrittenInConfiguration() {
            assertThat(PrincipalId.parse("iam#" + ROLE_ARN))
                    .isEqualTo(PrincipalId.forIamRole(ROLE_ARN));
        }

        @Test
        void shouldMatchATokenSubjectWrittenInConfiguration() {
            assertThat(PrincipalId.parse("jwt#" + ISSUER + "#a1b2c3"))
                    .isEqualTo(PrincipalId.forToken(ISSUER, "a1b2c3"));
        }

        @Test
        void shouldReadBackAPrincipalItWrote() {
            PrincipalId principal = PrincipalId.forToken(ISSUER, "a1b2c3");

            assertThat(PrincipalId.parse(principal.toString())).isEqualTo(principal);
        }
    }

    @Nested
    @DisplayName("Keep principals apart")
    class KeepPrincipalsApart {

        @Test
        void shouldNotMatchTheSameSubjectFromADifferentIssuer() {
            assertThat(PrincipalId.forToken(ISSUER, "a1b2c3"))
                    .isNotEqualTo(PrincipalId.forToken("https://somewhere.else", "a1b2c3"));
        }

        @Test
        void shouldNotMatchADifferentSubjectFromTheSameIssuer() {
            assertThat(PrincipalId.forToken(ISSUER, "a1b2c3"))
                    .isNotEqualTo(PrincipalId.forToken(ISSUER, "d4e5f6"));
        }

        @Test
        void shouldNotMatchARoleAgainstATokenSubjectOfTheSameName() {
            assertThat(PrincipalId.parse("iam#" + ROLE_ARN))
                    .isNotEqualTo(PrincipalId.parse("jwt#" + ROLE_ARN + "#a1b2c3"));
        }
    }

    @Nested
    @DisplayName("Report how a client authenticated")
    class ReportHowClientAuthenticated {

        @Test
        void shouldReportARoleAsAuthenticatingWithIam() {
            assertThat(PrincipalId.forIamRole(ROLE_ARN).getType()).isEqualTo(PrincipalType.IAM);
        }

        @Test
        void shouldReportATokenSubjectAsAuthenticatingWithAToken() {
            assertThat(PrincipalId.forToken(ISSUER, "a1b2c3").getType()).isEqualTo(PrincipalType.JWT);
        }
    }

    @Nested
    @DisplayName("Refuse a principal that cannot be read")
    class RefusePrincipalThatCannotBeRead {

        @ParameterizedTest
        @ValueSource(strings = {
            "arn:aws:iam::123456789012:role/SleeperAdmin",
            "saml#something",
            "#something",
            "iam#",
            "iam",
            "   "})
        void shouldRefuseAPrincipalThatDoesNotSayHowTheClientAuthenticates(String principal) {
            assertThatThrownBy(() -> PrincipalId.parse(principal))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRefuseNothing() {
            assertThatThrownBy(() -> PrincipalId.parse(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRefuseARoleWithNoArn() {
            assertThatThrownBy(() -> PrincipalId.forIamRole("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRefuseATokenSubjectWithNoIssuer() {
            assertThatThrownBy(() -> PrincipalId.forToken(null, "a1b2c3"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRefuseATokenSubjectWithNoSubject() {
            assertThatThrownBy(() -> PrincipalId.forToken(ISSUER, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
