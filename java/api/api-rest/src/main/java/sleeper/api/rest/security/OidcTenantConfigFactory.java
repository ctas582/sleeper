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
import io.quarkus.oidc.OidcTenantConfigBuilder;
import io.quarkus.oidc.runtime.OidcTenantConfig.ApplicationType;

import sleeper.core.properties.model.OidcIssuerConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Turns an issuer configured for a Sleeper instance into the configuration Quarkus verifies its tokens with.
 */
public class OidcTenantConfigFactory {

    /**
     * The claim roles are read from. Deliberately one no issuer sets, because a role read from a token would let any
     * issuer configured for an instance grant itself access to it.
     * <p>
     * This only keeps roles out of the identity in the first place, and does so by naming a claim an issuer is
     * unlikely to use rather than by switching the behaviour off, which the configuration offers no way to do.
     * PermissionAugmentor is what actually guarantees a client holds no role they were not granted, by building the
     * identity it serves the request with from nothing.
     */
    static final String CLAIM_THAT_CARRIES_NO_ROLES = "sleeper-never-reads-roles-from-a-token";

    private OidcTenantConfigFactory() {
    }

    /**
     * Builds the configuration to verify tokens from an issuer.
     *
     * @param  issuer     the issuer as configured for the instance
     * @param  signingKey the key the token being verified was signed with, or null to fetch the issuer's keys
     * @return            the configuration
     */
    public static OidcTenantConfig configFor(OidcIssuerConfig issuer, String signingKey) {
        OidcTenantConfigBuilder builder = OidcTenantConfig.builder()
                .tenantId(tenantId(issuer, signingKey))
                .enableTenant()
                .applicationType(ApplicationType.SERVICE);

        if (signingKey != null) {
            builder.publicKey(signingKey);
        } else {
            builder.jwksPath(issuer.jwksUri());
        }

        builder.token()
                .issuer(issuer.issuer())
                .audience(List.copyOf(issuer.audiences()))
                .principalClaim(issuer.principalClaim())
                .lifespanGrace(issuer.clockSkewSeconds())
                .end();

        builder.roles().roleClaimPath(CLAIM_THAT_CARRIES_NO_ROLES).end();

        return builder.build();
    }

    /**
     * Names the tenant after everything it was built from, so that an instance whose configuration has been edited is
     * never verified against the configuration it had before.
     * <p>
     * Quarkus keeps the first configuration it is given under a tenant name and reuses it for the life of the process,
     * ignoring any later configuration under that name. Naming the tenant after its contents means an edited issuer
     * arrives as a tenant Quarkus has not seen, so the edit takes effect on the next request.
     *
     * @param  issuer     the issuer as configured for the instance
     * @param  signingKey the key the token being verified was signed with, or null to fetch the issuer's keys
     * @return            the tenant name
     */
    private static String tenantId(OidcIssuerConfig issuer, String signingKey) {
        String contents = String.join("\n", issuer.name(), issuer.issuer(),
                String.join(",", issuer.audiences()), issuer.principalClaim(),
                Integer.toString(issuer.clockSkewSeconds()),
                String.valueOf(issuer.jwksUri()), String.valueOf(signingKey));
        return issuer.name() + "#" + digestOf(contents);
    }

    private static String digestOf(String contents) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(contents.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to be present in every Java runtime", e);
        }
    }

}
