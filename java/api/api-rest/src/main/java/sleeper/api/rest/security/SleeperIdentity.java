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

import io.quarkus.security.identity.SecurityIdentity;

import sleeper.api.auth.PrincipalId;
import sleeper.api.auth.PrincipalPermissions;

import java.util.Optional;

/**
 * Who a client is, and what they are allowed to do, on the identity Quarkus holds for a request.
 */
public class SleeperIdentity {

    static final String PRINCIPAL = "sleeper.principal";
    static final String PERMISSIONS = "sleeper.permissions";

    private SleeperIdentity() {
    }

    /**
     * Retrieves who the client is.
     *
     * @param  identity the identity for the request
     * @return          the principal, or an empty optional if the client did not authenticate
     */
    public static Optional<PrincipalId> principalOf(SecurityIdentity identity) {
        return Optional.ofNullable(identity).map(id -> id.<PrincipalId>getAttribute(PRINCIPAL));
    }

    /**
     * Retrieves what the client is allowed to do.
     *
     * @param  identity the identity for the request
     * @return          what they are allowed to do
     */
    public static PrincipalPermissions permissionsOf(SecurityIdentity identity) {
        return Optional.ofNullable(identity)
                .map(id -> id.<PrincipalPermissions>getAttribute(PERMISSIONS))
                .orElseGet(PrincipalPermissions::none);
    }

}
