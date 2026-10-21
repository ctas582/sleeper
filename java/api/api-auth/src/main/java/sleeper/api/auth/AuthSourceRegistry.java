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
package sleeper.api.auth;

import sleeper.core.properties.instance.InstanceProperties;
import sleeper.core.properties.model.OidcIssuerConfig;
import sleeper.core.properties.model.OidcIssuerConfigSerDe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static sleeper.core.properties.instance.ApiProperty.API_AUTH_COGNITO_ENABLED;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_IAM_ENABLED;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_OIDC_ISSUERS;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_COGNITO_CLIENT_ID;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_COGNITO_ISSUER;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_COGNITO_LOGIN_URL;

/**
 * The ways a client may authenticate with an instance's REST API.
 */
public class AuthSourceRegistry {

    public static final String COGNITO_ISSUER_NAME = "cognito";

    private final Map<String, OidcIssuerConfig> issuersByIssuer;
    private final boolean iamEnabled;

    private AuthSourceRegistry(List<OidcIssuerConfig> issuers, boolean iamEnabled) {
        this.issuersByIssuer = issuers.stream().collect(Collectors.toMap(
                OidcIssuerConfig::issuer, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        this.iamEnabled = iamEnabled;
    }

    /**
     * Reads the authentication sources configured for an instance.
     *
     * @param  properties the instance properties
     * @return            the sources
     */
    public static AuthSourceRegistry from(InstanceProperties properties) {
        List<OidcIssuerConfig> issuers = new ArrayList<>(
                OidcIssuerConfigSerDe.readList(properties.get(API_AUTH_OIDC_ISSUERS)));

        if (properties.getBoolean(API_AUTH_COGNITO_ENABLED)) {
            cognitoIssuer(properties).ifPresent(issuers::add);
        }

        return new AuthSourceRegistry(issuers, properties.getBoolean(API_AUTH_IAM_ENABLED));
    }

    /**
     * Describes the Cognito user pool created for the instance as one of the issuers it trusts, so that its tokens
     * are verified the same way as any other issuer.
     *
     * @param  properties the instance properties
     * @return            the issuer, or an empty optional if the user pool has not been deployed
     */
    private static Optional<OidcIssuerConfig> cognitoIssuer(InstanceProperties properties) {
        String issuer = properties.get(REST_API_COGNITO_ISSUER);
        String clientId = properties.get(REST_API_COGNITO_CLIENT_ID);
        if (issuer == null || issuer.isBlank() || clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        String loginUrl = properties.get(REST_API_COGNITO_LOGIN_URL);

        OidcIssuerConfig.Builder builder = OidcIssuerConfig.builder()
                .name(COGNITO_ISSUER_NAME)
                .issuer(issuer)
                .audiences(List.of(clientId))
                .jwksUri(issuer + "/.well-known/jwks.json")
                .clientId(clientId)
                .requiredClaims(Map.of("token_use", "id"));

        if (loginUrl != null && !loginUrl.isBlank()) {
            builder.authEndpoint(loginUrl + "/oauth2/authorize")
                    .tokenEndpoint(loginUrl + "/oauth2/token");
        }

        return Optional.of(builder.build());
    }

    /**
     * Finds the configuration to verify a token from the given issuer with.
     *
     * @param  issuer the issuer a token claims to come from
     * @return        the configuration, or an empty optional if that issuer is not trusted
     */
    public Optional<OidcIssuerConfig> forIssuer(String issuer) {
        return issuer == null ? Optional.empty() : Optional.ofNullable(issuersByIssuer.get(issuer));
    }

    /**
     * Retrieves the trusted issuers, in the order they were configured.
     *
     * @return the issuers
     */
    public List<OidcIssuerConfig> issuers() {
        return List.copyOf(issuersByIssuer.values());
    }

    public boolean isIamEnabled() {
        return iamEnabled;
    }

    /**
     * Checks whether a client has any way to authenticate.
     *
     * @return true if there is at least one way to authenticate
     */
    public boolean isConfigured() {
        return !issuersByIssuer.isEmpty() || iamEnabled;
    }

}
