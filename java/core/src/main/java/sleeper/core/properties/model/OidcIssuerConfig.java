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
package sleeper.core.properties.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * Configuration for a single OIDC issuer whose JSON Web Tokens the Sleeper REST API will accept.
 * <p>
 * An issuer establishes only who a client is. It never grants any permissions, as Sleeper roles come solely from the
 * principal allow-list. This means a claim in a token issued by a configured issuer cannot grant access to Sleeper.
 * <p>
 * Signing keys are supplied either by a JWKS URI the API will fetch, or by a public key held directly in the
 * configuration. The public key avoids any network call to the issuer, which is needed when the API cannot reach the
 * issuer.
 *
 * @param name              the name identifying this issuer in Sleeper configuration and in API responses
 * @param issuer            the issuer identifier, which must exactly match the iss claim in tokens from this issuer
 * @param audiences         the audiences accepted in the aud claim, of which a token must carry at least one
 * @param jwksUri           the URI to fetch this issuer's JSON Web Key Set from, or null if a public key is set
 * @param publicKey         this issuer's signing key, held directly so that no network call is needed, or null if a
 *                          JWKS URI is set
 * @param tokenEndpoint     the endpoint where automation can exchange client credentials for a token, reported by the
 *                          API so that scripts do not need to hard code it, or null if not known
 * @param principalClaim    the claim holding the identifier of the client, used to look them up in the principal
 *                          allow-list
 * @param clockSkewSeconds  how much clock difference to tolerate when checking token expiry
 */
public record OidcIssuerConfig(
        String name,
        String issuer,
        List<String> audiences,
        String jwksUri,
        String publicKey,
        String tokenEndpoint,
        String principalClaim,
        int clockSkewSeconds) {

    public static final String DEFAULT_PRINCIPAL_CLAIM = "sub";
    public static final int DEFAULT_CLOCK_SKEW_SECONDS = 60;
    public static final int MAX_CLOCK_SKEW_SECONDS = 300;

    public OidcIssuerConfig {
        requireNonBlank(name, "name");
        requireNonBlank(issuer, "issuer");
        requireAbsoluteUri(issuer, "issuer");
        if (audiences == null || audiences.isEmpty()) {
            throw new IllegalArgumentException("OIDC issuer \"" + name + "\" must set at least one audience. " +
                    "Without an audience, a token minted for any other application by the same issuer would be " +
                    "accepted by Sleeper.");
        }
        audiences.forEach(audience -> requireNonBlank(audience, "audience"));
        if (jwksUri == null && publicKey == null) {
            throw new IllegalArgumentException("OIDC issuer \"" + name + "\" must set either jwksUri or publicKey, " +
                    "to supply the keys its tokens are signed with.");
        }
        if (jwksUri != null && publicKey != null) {
            throw new IllegalArgumentException("OIDC issuer \"" + name + "\" must set only one of jwksUri or " +
                    "publicKey, but both were set.");
        }
        if (jwksUri != null) {
            requireAbsoluteUri(jwksUri, "jwksUri");
            if (!jwksUri.toLowerCase(Locale.ROOT).startsWith("https://")) {
                throw new IllegalArgumentException("OIDC issuer \"" + name + "\" must use https for jwksUri, as the " +
                        "keys fetched from it determine which tokens are trusted. Found: " + jwksUri);
            }
        }
        if (tokenEndpoint != null) {
            requireAbsoluteUri(tokenEndpoint, "tokenEndpoint");
        }
        requireNonBlank(principalClaim, "principalClaim");
        if (clockSkewSeconds < 0 || clockSkewSeconds > MAX_CLOCK_SKEW_SECONDS) {
            throw new IllegalArgumentException("OIDC issuer \"" + name + "\" must set clockSkewSeconds between 0 and " +
                    MAX_CLOCK_SKEW_SECONDS + ", found: " + clockSkewSeconds);
        }
        audiences = List.copyOf(audiences);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks whether this issuer supplies its signing keys without the API needing to reach the issuer. This is
     * required when the API is deployed where it has no network access to the issuer.
     *
     * @return true if the signing key is held in the configuration
     */
    public boolean isSelfContained() {
        return publicKey != null;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OIDC issuer " + field + " must be set.");
        }
    }

    private static void requireAbsoluteUri(String value, String field) {
        try {
            if (!new URI(value).isAbsolute()) {
                throw new IllegalArgumentException("OIDC issuer " + field + " must be an absolute URI, found: " + value);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("OIDC issuer " + field + " must be a valid URI, found: " + value, e);
        }
    }

    /**
     * Builds an OIDC issuer configuration, applying defaults for anything not set.
     */
    public static class Builder {
        private String name;
        private String issuer;
        private List<String> audiences;
        private String jwksUri;
        private String publicKey;
        private String tokenEndpoint;
        private String principalClaim = DEFAULT_PRINCIPAL_CLAIM;
        private int clockSkewSeconds = DEFAULT_CLOCK_SKEW_SECONDS;

        private Builder() {
        }

        /**
         * Sets the name identifying this issuer in Sleeper configuration and in API responses.
         *
         * @param  name the name
         * @return      this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the issuer identifier, which must exactly match the iss claim in tokens from this issuer.
         *
         * @param  issuer the issuer identifier
         * @return        this builder
         */
        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        /**
         * Sets the audiences accepted in the aud claim. A token must carry at least one of these.
         *
         * @param  audiences the audiences
         * @return           this builder
         */
        public Builder audiences(List<String> audiences) {
            this.audiences = audiences;
            return this;
        }

        /**
         * Sets the URI to fetch this issuer's JSON Web Key Set from. Mutually exclusive with the public key.
         *
         * @param  jwksUri the JWKS URI
         * @return         this builder
         */
        public Builder jwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
            return this;
        }

        /**
         * Sets this issuer's signing keys directly, so no network call to the issuer is needed. Holds either a
         * single key or a whole JSON Web Key Set, so an issuer the API cannot reach can still rotate its keys.
         * Mutually exclusive with the JWKS URI.
         *
         * @param  publicKey the key, or a JSON Web Key Set
         * @return           this builder
         */
        public Builder publicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        /**
         * Sets the endpoint where automation can exchange client credentials for a token. Reported by the API so
         * scripts do not need to hard code it.
         *
         * @param  tokenEndpoint the token endpoint
         * @return               this builder
         */
        public Builder tokenEndpoint(String tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
            return this;
        }

        /**
         * Sets the claim holding the identifier of the client, used to look them up in the principal allow-list.
         *
         * @param  principalClaim the claim name
         * @return                this builder
         */
        public Builder principalClaim(String principalClaim) {
            this.principalClaim = principalClaim;
            return this;
        }

        /**
         * Sets how much clock difference to tolerate when checking token expiry.
         *
         * @param  clockSkewSeconds the tolerance in seconds
         * @return                  this builder
         */
        public Builder clockSkewSeconds(int clockSkewSeconds) {
            this.clockSkewSeconds = clockSkewSeconds;
            return this;
        }

        public OidcIssuerConfig build() {
            return new OidcIssuerConfig(name, issuer, audiences, jwksUri, publicKey, tokenEndpoint,
                    principalClaim, clockSkewSeconds);
        }
    }

}
