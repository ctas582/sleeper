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

import sleeper.api.rest.testutils.LocalStackTestResource;
import sleeper.core.properties.instance.InstanceProperties;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.createConfigBucket;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.instance;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.saveProperties;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_IAM_ENABLED;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_OIDC_ISSUERS;

@QuarkusTest
@QuarkusTestResource(LocalStackTestResource.class)
class AuthInfoResourceIT {

    private static final String ISSUER = "https://idp.internal/realms/sleeper";

    @BeforeEach
    void setUp() {
        createConfigBucket();
    }

    @Test
    void shouldReportAnIssuerThatWasConfigured() {
        InstanceProperties properties = instance();
        properties.set(API_AUTH_OIDC_ISSUERS, """
                [{"name": "corp", "issuer": "%s", "audiences": ["sleeper-api"],
                  "jwksUri": "https://idp.internal/certs",
                  "tokenEndpoint": "https://idp.internal/token"}]
                """.formatted(ISSUER));
        saveProperties(properties);

        given()
                .when().get("/api/v1/auth/info")
                .then()
                .statusCode(200)
                .body("configured", is(true))
                .body("issuers.name", contains("corp"))
                .body("issuers.issuer", contains(ISSUER))
                .body("issuers.tokenEndpoint", contains("https://idp.internal/token"))
                .body("error", is(nullValue()));
    }

    @Test
    void shouldReportThatAwsIamRoleMayAuthenticateWhenNoIssuerIsConfigured() {
        saveProperties(instance());

        given()
                .when().get("/api/v1/auth/info")
                .then()
                .statusCode(200)
                .body("configured", is(true))
                .body("iamEnabled", is(true))
                .body("issuers", is(List.of()));
    }

    @Test
    void shouldNotReportAnythingSecretAboutAnIssuer() {
        InstanceProperties properties = instance();
        properties.set(API_AUTH_OIDC_ISSUERS, """
                [{"name": "corp", "issuer": "%s", "audiences": ["a-private-audience"],
                  "publicKey": "a-key-that-should-not-be-reported"}]
                """.formatted(ISSUER));
        saveProperties(properties);

        String body = given().when().get("/api/v1/auth/info").then().statusCode(200).extract().asString();

        assertThat(body)
                .doesNotContain("a-key-that-should-not-be-reported")
                .doesNotContain("a-private-audience");
    }

    @Test
    void shouldSayHowToConfigureAnAuthenticationSourceWhenNobodyCanAuthenticate() {
        InstanceProperties properties = instance();
        properties.set(API_AUTH_IAM_ENABLED, "false");
        saveProperties(properties);

        given()
                .when().get("/api/v1/auth/info")
                .then()
                .statusCode(200)
                .body("configured", is(false))
                .body("error.error", is("auth_not_configured"))
                .body("error.remediation", containsString("sleeper.rest.api.auth.oidc.issuers"))
                .body("error.remediation", containsString("sleeper.rest.api.auth.iam.enabled"));
    }

}
