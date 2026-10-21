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
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import sleeper.api.auth.AuthSourceRegistry;
import sleeper.api.rest.ApiError;
import sleeper.api.rest.config.InstancePropertiesProvider;
import sleeper.core.properties.model.OidcIssuerConfig;

import java.util.List;

import static sleeper.core.properties.instance.ApiProperty.API_AUTH_COGNITO_ENABLED;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_IAM_ENABLED;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_OIDC_ISSUERS;

/**
 * Reports how a client may authenticate.
 */
@Path("/api/v1/auth/info")
public class AuthInfoResource {

    private static final String NOT_CONFIGURED = "auth_not_configured";

    private final InstancePropertiesProvider instanceProperties;

    @Inject
    public AuthInfoResource(InstancePropertiesProvider instanceProperties) {
        this.instanceProperties = instanceProperties;
    }

    /**
     * Reports how a client may authenticate with this instance.
     *
     * @return the ways a client may authenticate
     */
    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public AuthInfo get() {
        AuthSourceRegistry sources = AuthSourceRegistry.from(instanceProperties.get());
        return new AuthInfo(
                sources.isConfigured(),
                sources.issuers().stream().map(AuthInfoResource::describe).toList(),
                sources.isIamEnabled(),
                sources.isConfigured() ? null : notConfigured());
    }

    private static Issuer describe(OidcIssuerConfig issuer) {
        return new Issuer(issuer.name(), issuer.issuer(), issuer.tokenEndpoint(),
                issuer.authEndpoint(), issuer.clientId());
    }

    private static ApiError notConfigured() {
        return new ApiError(NOT_CONFIGURED,
                "This Sleeper instance has no way for a client to authenticate.",
                "Configure an OIDC issuer in " + API_AUTH_OIDC_ISSUERS.getPropertyName()
                        + ", or set " + API_AUTH_IAM_ENABLED.getPropertyName()
                        + " to allow authentication using an AWS IAM role, or set "
                        + API_AUTH_COGNITO_ENABLED.getPropertyName()
                        + " to have Sleeper create a user pool.");
    }

    /**
     * How a client may authenticate with this instance.
     *
     * @param configured whether there is any way to authenticate at all
     * @param issuers    the OIDC issuers whose tokens are accepted
     * @param iamEnabled whether authenticating using an AWS IAM role is possible
     * @param error      when nothing is configured
     */
    public record AuthInfo(boolean configured, List<Issuer> issuers, boolean iamEnabled, ApiError error) {
    }

    /**
     * An OIDC issuer whose tokens this instance accepts.
     *
     * @param name          the name identifying the issuer in this instance's configuration
     * @param issuer        the issuer identifier, matching the iss claim in its tokens
     * @param tokenEndpoint where automation can exchange client credentials for a token, if known
     * @param authEndpoint  where a browser sends someone to sign in, if known
     * @param clientId      the application a client signs in to, if known
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Issuer(String name, String issuer, String tokenEndpoint,
            String authEndpoint, String clientId) {
    }

}
