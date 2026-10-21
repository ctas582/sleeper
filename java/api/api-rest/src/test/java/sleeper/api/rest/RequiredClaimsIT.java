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
import static sleeper.api.rest.testutils.SleeperInstanceForTest.createConfigBucket;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.instance;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.saveProperties;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_ADMIN_PRINCIPALS;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_OIDC_ISSUERS;

/**
 * Checks that an issuer can be configured to accept only one kind of its tokens.
 * <p>
 * An issuer may sign several kinds of token for the same client, and they do not all carry the same claims. A Cognito
 * user pool is the case this exists for: it names the application a client signed in to in the audience of the token
 * identifying who they are, but not in the token authorising them to call an API. Accepting that second kind would
 * mean accepting a token that had never been checked against an audience, so the API has to be able to tell them
 * apart.
 */
@QuarkusTest
@QuarkusTestResource(LocalStackTestResource.class)
class RequiredClaimsIT {

    private static final String ISSUER = "https://idp.internal/realms/sleeper";
    private static final String AUDIENCE = "sleeper-api";
    private static final String SUBJECT = "a1b2c3";

    private final TestJwts idp = TestJwts.issuer();

    @BeforeEach
    void setUp() {
        createConfigBucket();
    }

    @Test
    void shouldAcceptATokenCarryingTheRequiredClaim() {
        trustIssuerRequiring("token_use", "id");

        given()
                .header("Authorization", "Bearer " + tokenWithUse("id"))
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(200);
    }

    @Test
    void shouldRefuseATokenOfADifferentKindToTheOneRequired() {
        trustIssuerRequiring("token_use", "id");

        given()
                .header("Authorization", "Bearer " + tokenWithUse("access"))
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void shouldRefuseATokenMissingTheRequiredClaimEntirely() {
        trustIssuerRequiring("token_use", "id");

        given()
                .header("Authorization", "Bearer " + idp.tokenForAudience(ISSUER, SUBJECT, AUDIENCE))
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void shouldAcceptATokenOfAnyKindWhenTheIssuerRequiresNoFurtherClaims() {
        trustIssuer();

        given()
                .header("Authorization", "Bearer " + tokenWithUse("access"))
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(200);
    }

    private String tokenWithUse(String tokenUse) {
        return idp.token(ISSUER, SUBJECT, claims -> claims
                .audience(AUDIENCE)
                .claim("token_use", tokenUse));
    }

    private void trustIssuer() {
        setIssuers("""
                [{"name": "corp", "issuer": "%s", "audiences": ["%s"], "publicKey": %s}]
                """.formatted(ISSUER, AUDIENCE, idp.publicJwks()));
    }

    private void trustIssuerRequiring(String claim, String value) {
        setIssuers("""
                [{"name": "corp", "issuer": "%s", "audiences": ["%s"], "publicKey": %s,
                  "requiredClaims": {"%s": "%s"}}]
                """.formatted(ISSUER, AUDIENCE, idp.publicJwks(), claim, value));
    }

    private void setIssuers(String issuers) {
        InstanceProperties properties = instance();
        properties.set(API_AUTH_OIDC_ISSUERS, issuers);
        properties.set(API_AUTH_ADMIN_PRINCIPALS, "jwt#" + ISSUER + "#" + SUBJECT);
        saveProperties(properties);
    }

}

