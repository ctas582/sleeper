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

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import sleeper.api.auth.PrincipalId;
import sleeper.api.auth.PrincipalPermissions;
import sleeper.api.auth.SleeperPermission;
import sleeper.api.auth.store.AdminPrincipalsStore;
import sleeper.api.rest.config.InstancePropertiesProvider;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Decides what a client is allowed to do, once it is known who they are.
 * Permissions are looked up against the principal, never taken from the token the client presented.
 */
@ApplicationScoped
public class PermissionAugmentor implements SecurityIdentityAugmentor {

    private final InstancePropertiesProvider instanceProperties;

    @Inject
    public PermissionAugmentor(InstancePropertiesProvider instanceProperties) {
        this.instanceProperties = instanceProperties;
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        return principalOf(identity)
                .map(principal -> context.runBlocking(() -> withPermissions(identity, principal)))
                .orElseGet(() -> Uni.createFrom().item(identity));
    }

    /**
     * Works out who the client is.
     *
     * @param  identity the identity for the request
     * @return          who the client is, or an empty optional if they did not authenticate
     */
    private static Optional<PrincipalId> principalOf(SecurityIdentity identity) {
        return SleeperIdentity.principalOf(identity)
                .or(() -> tokenPrincipalOf(identity));
    }

    /**
     * Identifies a client from the token they presented.
     *
     * @param  identity the identity for the request
     * @return          who the client is, or an empty optional if they did not present a token
     */
    private static Optional<PrincipalId> tokenPrincipalOf(SecurityIdentity identity) {
        if (identity == null || !(identity.getPrincipal() instanceof JsonWebToken token)) {
            return Optional.empty();
        }
        if (token.getIssuer() == null || token.getName() == null) {
            return Optional.empty();
        }
        return Optional.of(PrincipalId.forToken(token.getIssuer(), token.getName()));
    }

    /**
     * Looks up what the client is allowed to do, and builds an identity carrying only that access.
     *
     * @param  identity  the identity for the request
     * @param  principal who the client is
     * @return           the identity, carrying what they are allowed to do
     */
    private SecurityIdentity withPermissions(SecurityIdentity identity, PrincipalId principal) {
        PrincipalPermissions permissions = AdminPrincipalsStore.from(instanceProperties.get()).permissionsFor(principal);
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                .setPrincipal(identity.getPrincipal())
                .addAttributes(identity.getAttributes())
                .addCredentials(identity.getCredentials())
                .addAttribute(SleeperIdentity.PRINCIPAL, principal)
                .addAttribute(SleeperIdentity.PERMISSIONS, permissions);
        instanceWidePermissions()
                .filter(permissions::allows)
                .forEach(permission -> builder.addRole(permission.name()));
        return builder.build();
    }

    private static Stream<SleeperPermission> instanceWidePermissions() {
        return Arrays.stream(SleeperPermission.values()).filter(permission -> !permission.isTableScoped());
    }

}
