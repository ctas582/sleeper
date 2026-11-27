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

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Checks which websites a browser will let make requests to the API on behalf of a signed in client.
 */
@QuarkusTest
@TestProfile(CorsTest.AllowOneOrigin.class)
class CorsTest {

    private static final String ALLOWED = "https://ui.internal";

    /**
     * Configures the instance to allow requests from one origin.
     */
    public static class AllowOneOrigin implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.cors.origins", ALLOWED);
        }
    }

    @Test
    void shouldAllowAPreflightFromTheConfiguredOrigin() {
        given()
                .header("Origin", ALLOWED)
                .header("Access-Control-Request-Method", "GET")
                .when().options("/api/v1/instance/properties")
                .then()
                .statusCode(200)
                .header("access-control-allow-origin", is(ALLOWED));
    }

    @Test
    void shouldRefuseAPreflightFromAnyOtherOrigin() {
        given()
                .header("Origin", "https://somewhere.else")
                .header("Access-Control-Request-Method", "GET")
                .when().options("/api/v1/instance/properties")
                .then()
                .statusCode(403);
    }

    @Test
    void shouldStillRequireAuthenticationForTheRequestThatFollowsAPreflight() {
        // Allowing the origin says the browser may send the request. It does not say the client may make it.
        given()
                .header("Origin", ALLOWED)
                .when().get("/api/v1/instance/properties")
                .then()
                .statusCode(401);
    }

}
