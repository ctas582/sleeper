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

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import sleeper.api.rest.config.InstancePropertiesProvider;
import sleeper.core.properties.instance.InstanceProperties;

import java.util.Properties;

/**
 * Reports the configuration of the Sleeper instance.
 */
@Path("/api/v1/instance/properties")
public class InstancePropertiesResource {

    private final InstancePropertiesProvider instanceProperties;

    @Inject
    public InstancePropertiesResource(InstancePropertiesProvider instanceProperties) {
        this.instanceProperties = instanceProperties;
    }

    /**
     * Reports the properties set for the instance, with the value of each.
     *
     * @return the properties
     */
    @GET
    @RolesAllowed("READ_INSTANCE_CONFIGURATION")
    @Produces(MediaType.APPLICATION_JSON)
    public Properties get() {
        InstanceProperties properties = instanceProperties.get();
        return properties.getProperties();
    }

}
