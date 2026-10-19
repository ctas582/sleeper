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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.createConfigBucket;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.instance;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.saveProperties;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_ADMIN_PRINCIPALS;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_OIDC_ISSUERS;

/**
 * Checks that a client presenting a token is authenticated against the issuers its instance trusts.
 */
@QuarkusTest
@QuarkusTestResource(LocalStackTestResource.class)
class TokenAuthenticationIT {

    private static final String ISSUER = "https://idp.internal/realms/sleeper";
    private static final String AUDIENCE = "sleeper-api";
    private static final String SUBJECT = "a1b2c3";

    private final TestJwts idp = TestJwts.issuer();

    @BeforeEach
    void setUp() {
        createConfigBucket();
    }

    @Test
    void shouldIdentifyAClientPresentingATokenFromATrustedIssuer() {
        trustIssuer(idp, "jwt#" + ISSUER + "#" + SUBJECT);

        given()
                .header("Authorization", "Bearer " + idp.tokenForAudience(ISSUER, SUBJECT, AUDIENCE))
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(200)
                .body("principal", is("jwt#" + ISSUER + "#" + SUBJECT))
                .body("authenticatedBy", is("JWT"))
                .body("admin", is(true))
                .body("permissions.permission", contains("ADMIN"));
    }

    @Test
    void shouldAllowNothingToAClientWhoIsNotInTheAllowList() {
        trustIssuer(idp, "");

        given()
                .header("Authorization", "Bearer " + idp.tokenForAudience(ISSUER, SUBJECT, AUDIENCE))
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(200)
                .body("admin", is(false))
                .body("permissions", is(java.util.List.of()));
    }

    @Test
    void shouldRefuseAResourceTheClientHasNotBeenGrantedAccessTo() {
        trustIssuer(idp, "");

        given()
                .header("Authorization", "Bearer " + idp.tokenForAudience(ISSUER, SUBJECT, AUDIENCE))
                .when().get("/api/v1/instance/properties")
                .then()
                .statusCode(403);
    }

    @Test
    void shouldAllowAResourceTheClientHasBeenGrantedAccessTo() {
        trustIssuer(idp, "jwt#" + ISSUER + "#" + SUBJECT);

        given()
                .header("Authorization", "Bearer " + idp.tokenForAudience(ISSUER, SUBJECT, AUDIENCE))
                .when().get("/api/v1/instance/properties")
                .then()
                .statusCode(200);
    }

    @Test
    void shouldRefuseATokenFromAnIssuerTheInstanceDoesNotTrust() {
        TestJwts somewhereElse = TestJwts.issuer();
        trustIssuer(idp, "jwt#https://somewhere.else#" + SUBJECT);

        given()
                .header("Authorization", "Bearer " + somewhereElse.tokenForAudience("https://somewhere.else", SUBJECT, AUDIENCE))
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void shouldRefuseATokenSignedByTheWrongKey() {
        TestJwts impostor = TestJwts.issuer();
        trustIssuer(idp, "jwt#" + ISSUER + "#" + SUBJECT);

        given()
                .header("Authorization", "Bearer " + impostor.tokenForAudience(ISSUER, SUBJECT, AUDIENCE))
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void shouldRefuseATokenMeantForAnotherApplication() {
        trustIssuer(idp, "jwt#" + ISSUER + "#" + SUBJECT);

        given()
                .header("Authorization", "Bearer " + idp.tokenForAudience(ISSUER, SUBJECT, "some-other-application"))
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void shouldRefuseATokenThatHasExpired() {
        trustIssuer(idp, "jwt#" + ISSUER + "#" + SUBJECT);
        String expired = idp.token(ISSUER, SUBJECT, claims -> claims
                .audience(AUDIENCE)
                .expirationTime(java.util.Date.from(java.time.Instant.now().minusSeconds(3600))));

        given()
                .header("Authorization", "Bearer " + expired)
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void shouldVerifyATokenAgainstTheRightKeyWhenTheIssuerIsRotatingThem() {
        // An issuer part way through a rotation publishes both keys. A token signed by either must verify, which is
        // what holding a whole key set rather than a single key is for.
        TestJwts oldKey = TestJwts.issuerWithKeyId("old");
        TestJwts newKey = TestJwts.issuerWithKeyId("new");
        trustIssuerWithKeys(TestJwts.publicJwks(oldKey, newKey), "jwt#" + ISSUER + "#" + SUBJECT);

        given()
                .header("Authorization", "Bearer " + oldKey.tokenForAudience(ISSUER, SUBJECT, AUDIENCE))
                .when().get("/api/v1/auth/me")
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + newKey.tokenForAudience(ISSUER, SUBJECT, AUDIENCE))
                .when().get("/api/v1/auth/me")
                .then().statusCode(200);
    }

    private void trustIssuer(TestJwts issuer, String administrators) {
        trustIssuerWithKeys(issuer.publicJwks(), administrators);
    }

    private void trustIssuerWithKeys(String jwks, String administrators) {
        InstanceProperties properties = instance();
        properties.set(API_AUTH_OIDC_ISSUERS, """
                [{"name": "corp", "issuer": "%s", "audiences": ["%s"], "publicKey": %s}]
                """.formatted(ISSUER, AUDIENCE, jwks));
        properties.set(API_AUTH_ADMIN_PRINCIPALS, administrators);
        saveProperties(properties);
    }

}
