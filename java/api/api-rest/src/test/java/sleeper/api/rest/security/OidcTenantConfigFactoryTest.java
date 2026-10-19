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

import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.runtime.OidcTenantConfig.ApplicationType;
import org.junit.jupiter.api.Test;

import sleeper.core.properties.model.OidcIssuerConfig;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that an issuer configured for an instance is correctly converted into configuration Quarkus requires.
 */
class OidcTenantConfigFactoryTest {

    private static final String ISSUER = "https://idp.internal/realms/sleeper";
    private static final String JWKS_URI = "https://idp.internal/realms/sleeper/certs";

    @Test
    void shouldVerifyTokensAgainstTheKeyThatSignedThemWhenTheIssuerIsNotFetchedFrom() {
        OidcTenantConfig config = OidcTenantConfigFactory.configFor(issuer()
                .publicKey("{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"one\"}]}")
                .build(), "the-key-that-signed-this-token");

        assertThat(config.publicKey()).contains("the-key-that-signed-this-token");
        assertThat(config.jwksPath()).isEmpty();
    }

    @Test
    void shouldVerifyTokensAgainstKeysFetchedFromTheIssuer() {
        OidcTenantConfig config = OidcTenantConfigFactory.configFor(issuer().jwksUri(JWKS_URI).build(), null);

        assertThat(config.jwksPath()).contains(JWKS_URI);
        assertThat(config.publicKey()).isEmpty();
    }

    @Test
    void shouldOnlyAcceptTokensMeantForSleeper() {
        OidcTenantConfig config = OidcTenantConfigFactory.configFor(issuer()
                .jwksUri(JWKS_URI)
                .audiences(List.of("sleeper-api", "sleeper-ui"))
                .build(), null);

        assertThat(config.token().audience()).contains(List.of("sleeper-api", "sleeper-ui"));
    }

    @Test
    void shouldOnlyAcceptTokensFromTheIssuerItWasConfiguredFor() {
        OidcTenantConfig config = OidcTenantConfigFactory.configFor(issuer().jwksUri(JWKS_URI).build(), null);

        assertThat(config.token().issuer()).contains(ISSUER);
    }

    @Test
    void shouldReadNoRolesFromATokenHoweverTheIssuerDescribedThem() {
        OidcTenantConfig config = OidcTenantConfigFactory.configFor(issuer().jwksUri(JWKS_URI).build(), null);

        assertThat(config.roles().roleClaimPath())
                .contains(List.of(OidcTenantConfigFactory.CLAIM_THAT_CARRIES_NO_ROLES));
        assertThat(config.roles().roleClaimPath()).isNotEmpty()
                .get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .doesNotContain("groups");
    }

    @Test
    void shouldNeverSignAClientIn() {
        // The API reads tokens presented to it. Redirecting a client to the issuer is not its job.
        OidcTenantConfig config = OidcTenantConfigFactory.configFor(issuer().jwksUri(JWKS_URI).build(), null);

        assertThat(config.applicationType()).contains(ApplicationType.SERVICE);
    }

    @Test
    void shouldIdentifyTheClientByTheClaimTheIssuerWasConfiguredWith() {
        OidcTenantConfig config = OidcTenantConfigFactory.configFor(issuer()
                .jwksUri(JWKS_URI)
                .principalClaim("preferred_username")
                .build(), null);

        assertThat(config.token().principalClaim()).contains("preferred_username");
    }

    @Test
    void shouldAllowForClocksThatDisagree() {
        OidcTenantConfig config = OidcTenantConfigFactory.configFor(issuer()
                .jwksUri(JWKS_URI)
                .clockSkewSeconds(120)
                .build(), null);

        assertThat(config.token().lifespanGrace()).hasValue(120);
    }

    @Test
    void shouldNameTheTenantAfterTheIssuer() {
        OidcTenantConfig config = OidcTenantConfigFactory.configFor(issuer().jwksUri(JWKS_URI).build(), null);

        assertThat(config.tenantId()).get().asString().startsWith("corp#");
        assertThat(config.tenantEnabled()).isTrue();
    }

    @Test
    void shouldGiveAnEditedIssuerATenantNameItHasNotBeenSeenUnderBefore() {
        // Quarkus keeps the first configuration it is given under a tenant name for the life of the process. An
        // edited issuer must therefore arrive under a name it has not used before, or the edit never takes effect.
        assertThat(tenantIdsOf(
                issuer().jwksUri(JWKS_URI).build(),
                issuer().jwksUri("https://idp.internal/realms/sleeper/other-certs").build(),
                issuer().jwksUri(JWKS_URI).audiences(List.of("another-audience")).build(),
                issuer().jwksUri(JWKS_URI).principalClaim("preferred_username").build(),
                issuer().jwksUri(JWKS_URI).clockSkewSeconds(120).build(),
                issuer().issuer("https://idp.internal/realms/other").jwksUri(JWKS_URI).build()))
                .doesNotHaveDuplicates();
    }

    @Test
    void shouldGiveARotatedSigningKeyATenantNameItHasNotBeenSeenUnderBefore() {
        OidcIssuerConfig issuer = issuer().publicKey("{\"keys\":[]}").build();

        assertThat(OidcTenantConfigFactory.configFor(issuer, "the-old-key").tenantId())
                .isNotEqualTo(OidcTenantConfigFactory.configFor(issuer, "the-new-key").tenantId());
    }

    @Test
    void shouldGiveAnUnchangedIssuerTheSameTenantNameSoItIsNotRebuiltOnEveryRequest() {
        OidcIssuerConfig issuer = issuer().jwksUri(JWKS_URI).build();

        assertThat(OidcTenantConfigFactory.configFor(issuer, null).tenantId())
                .isEqualTo(OidcTenantConfigFactory.configFor(issuer, null).tenantId());
    }

    private static List<String> tenantIdsOf(OidcIssuerConfig... issuers) {
        return Stream.of(issuers)
                .map(issuer -> OidcTenantConfigFactory.configFor(issuer, null).tenantId().orElseThrow())
                .toList();
    }

    private static OidcIssuerConfig.Builder issuer() {
        return OidcIssuerConfig.builder()
                .name("corp")
                .issuer(ISSUER)
                .audiences(List.of("sleeper-api"));
    }

}
