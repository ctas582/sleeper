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
package sleeper.api.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sleeper.api.auth.testutils.TestJwts;
import sleeper.api.rest.testutils.LocalStackTestResource;
import sleeper.core.properties.instance.InstanceProperties;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.createConfigBucket;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.instance;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.saveProperties;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_ADMIN_PRINCIPALS;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_OIDC_ISSUERS;

/**
 * Checks that a client cannot grant themselves access by what they put in their token. An instance may trust several
 * issuers, and an issuer is trusted to say who a client is, not what they may do. Anyone who can mint a token from a
 * trusted issuer could otherwise claim any access they liked.
 */
@QuarkusTest
@QuarkusTestResource(LocalStackTestResource.class)
class TokenRolesIT {

    private static final String ISSUER = "https://idp.internal/realms/sleeper";
    private static final String AUDIENCE = "sleeper-api";
    private static final String SUBJECT = "a1b2c3";

    private final TestJwts idp = TestJwts.issuer();

    @BeforeEach
    void setUp() {
        createConfigBucket();
        trustIssuer();
    }

    @Test
    void shouldGrantNothingToAClientWhoseTokenClaimsTheyAreAnAdministrator() {
        String token = idp.token(ISSUER, SUBJECT, claims -> claims
                .audience(AUDIENCE)
                .claim("groups", List.of("ADMIN", "READ_INSTANCE_CONFIGURATION")));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(200)
                .body("admin", is(false))
                .body("permissions", is(List.of()));
    }

    @Test
    void shouldRefuseAResourceToAClientWhoseTokenClaimsTheyMayReadIt() {
        String token = idp.token(ISSUER, SUBJECT, claims -> claims
                .audience(AUDIENCE)
                .claim("groups", List.of("READ_INSTANCE_CONFIGURATION", "ADMIN")));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/api/v1/instance/properties")
                .then()
                .statusCode(403);
    }

    @Test
    void shouldGrantNothingFromClaimsAnIssuerMightUseForGroupsInsteadOfTheUsualOne() {
        String token = idp.token(ISSUER, SUBJECT, claims -> claims
                .audience(AUDIENCE)
                .claim("roles", List.of("ADMIN"))
                .claim("scope", "ADMIN READ_INSTANCE_CONFIGURATION")
                .claim("realm_access", java.util.Map.of("roles", List.of("ADMIN"))));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(200)
                .body("admin", is(false))
                .body("permissions", is(List.of()));
    }

    private void trustIssuer() {
        InstanceProperties properties = instance();
        properties.set(API_AUTH_OIDC_ISSUERS, """
                [{"name": "corp", "issuer": "%s", "audiences": ["%s"], "publicKey": %s}]
                """.formatted(ISSUER, AUDIENCE, idp.publicJwks()));
        properties.set(API_AUTH_ADMIN_PRINCIPALS, "");
        saveProperties(properties);
    }

}
