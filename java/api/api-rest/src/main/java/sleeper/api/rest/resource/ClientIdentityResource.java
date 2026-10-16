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
package sleeper.api.rest.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import sleeper.api.auth.PermissionGrant;
import sleeper.api.auth.PrincipalId;
import sleeper.api.auth.PrincipalPermissions;
import sleeper.api.auth.TableScope;
import sleeper.api.rest.security.SleeperIdentity;

import java.util.List;

/**
 * Reports who the client is and what they are allowed to do.
 */
@Path("/api/v1/auth/me")
public class ClientIdentityResource {

    private final SecurityIdentity identity;

    @Inject
    public ClientIdentityResource(SecurityIdentity identity) {
        this.identity = identity;
    }

    /**
     * Reports who the client is and what they are allowed to do.
     *
     * @return the client's identity
     */
    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public ClientIdentity get() {
        PrincipalPermissions permissions = SleeperIdentity.permissionsOf(identity);
        return new ClientIdentity(
                SleeperIdentity.principalOf(identity).map(PrincipalId::toString).orElse(null),
                SleeperIdentity.principalOf(identity).map(principal -> principal.getType().name()).orElse(null),
                permissions.isAdmin(),
                permissions.getGrants().stream()
                        .map(ClientIdentityResource::describe)
                        .sorted((left, right) -> left.permission().compareTo(right.permission()))
                        .toList());
    }

    private static GrantedPermission describe(PermissionGrant grant) {
        if (!grant.getPermission().isTableScoped()) {
            return new GrantedPermission(grant.getPermission().name(), null, null);
        }
        TableScope tables = grant.getTables();
        return tables.isAllTables()
                ? new GrantedPermission(grant.getPermission().name(), true, null)
                : new GrantedPermission(grant.getPermission().name(), null,
                        tables.tableIds().stream().sorted().toList());
    }

    /**
     * Who a client is and what they are allowed to do.
     *
     * @param principal       identifies the client, including which issuer identified them if they used a token
     * @param authenticatedBy how they proved who they are
     * @param admin           whether they are allowed to do everything
     * @param permissions     what they have been granted
     */
    public record ClientIdentity(
            String principal, String authenticatedBy, boolean admin, List<GrantedPermission> permissions) {
    }

    /**
     * A permission the client holds.
     *
     * @param permission the permission
     * @param allTables  set when the permission covers every table, including tables added later
     * @param tables     set when the permission covers particular tables
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GrantedPermission(String permission, Boolean allTables, List<String> tables) {
    }

}
