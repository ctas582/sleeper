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

import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TenantConfigResolver;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import sleeper.api.auth.AuthSourceRegistry;
import sleeper.api.auth.oidc.BearerTokenIssuerReader;
import sleeper.api.auth.oidc.IssuerSigningKeys;
import sleeper.api.rest.config.InstancePropertiesProvider;
import sleeper.core.properties.model.OidcIssuerConfig;

import java.util.Optional;

/**
 * Finds the configuration to verify a request's token with, from the issuers the instance trusts.
 */
@ApplicationScoped
public class SleeperTenantConfigResolver implements TenantConfigResolver {

    private final InstancePropertiesProvider instanceProperties;

    @Inject
    public SleeperTenantConfigResolver(InstancePropertiesProvider instanceProperties) {
        this.instanceProperties = instanceProperties;
    }

    @Override
    public Uni<OidcTenantConfig> resolve(RoutingContext context, OidcRequestContext<OidcTenantConfig> requestContext) {
        Optional<String> token = BearerTokenIssuerReader.tokenFromHeader(
                context.request().getHeader(HttpHeaders.AUTHORIZATION));
        if (token.isEmpty()) {
            return Uni.createFrom().nullItem();
        }
        return requestContext.runBlocking(() -> configFor(token.get()));
    }

    /**
     * Finds the configuration to verify a token with, from the issuer it claims to come from.
     *
     * @param  token the token presented by the client
     * @return       the configuration, or null if the instance cannot verify a token like this one
     */
    private OidcTenantConfig configFor(String token) {
        Optional<OidcIssuerConfig> found = BearerTokenIssuerReader.issuerOf(token)
                .flatMap(issuer -> AuthSourceRegistry.from(instanceProperties.get()).forIssuer(issuer));
        if (found.isEmpty()) {
            return null;
        }
        OidcIssuerConfig issuer = found.get();
        if (issuer.publicKey() == null) {
            // Quarkus will fetch and resolve the keys itself.
            return OidcTenantConfigFactory.configFor(issuer, null);
        }
        // The issuer's keys are held in the instance configuration, so the one the token names is picked out here.
        return IssuerSigningKeys.encodedKeyForToken(issuer.publicKey(), token)
                .map(key -> OidcTenantConfigFactory.configFor(issuer, key))
                .orElse(null);
    }

}
