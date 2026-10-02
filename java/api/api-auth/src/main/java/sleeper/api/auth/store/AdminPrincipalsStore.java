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
package sleeper.api.auth.store;

import sleeper.api.auth.PrincipalId;
import sleeper.api.auth.PrincipalPermissions;
import sleeper.core.properties.instance.InstanceProperties;

import java.util.LinkedHashSet;
import java.util.Set;

import static sleeper.core.properties.instance.ApiProperty.API_AUTH_ADMIN_PRINCIPALS;

/**
 * Grants administrator access to the principals listed in the instance properties.
 * <p>
 * This is how an administrator is granted access before anyone is able to call the API to grant access to others, and
 * the way back in if the managed allow-list is misconfigured.
 */
public class AdminPrincipalsStore implements PrincipalPermissionStore {

    private final Set<PrincipalId> admins;

    private AdminPrincipalsStore(Set<PrincipalId> admins) {
        this.admins = admins;
    }

    /**
     * Reads the administrators listed in the instance properties.
     *
     * @param  properties               the instance properties
     * @return                          the store
     * @throws IllegalArgumentException if a principal in the list could not be read
     */
    public static AdminPrincipalsStore from(InstanceProperties properties) {
        Set<PrincipalId> admins = new LinkedHashSet<>();
        properties.getList(API_AUTH_ADMIN_PRINCIPALS).forEach(principal -> admins.add(PrincipalId.parse(principal)));
        return new AdminPrincipalsStore(admins);
    }

    @Override
    public PrincipalPermissions permissionsFor(PrincipalId principal) {
        return admins.contains(principal) ? PrincipalPermissions.admin() : PrincipalPermissions.none();
    }

}
