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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Checks that the API refuses anything that has not been explicitly allowed.
 */
@QuarkusTest
class SecurityRulesTest {

    @Nested
    @DisplayName("Refuse a client that has not authenticated")
    class RefuseUnauthenticatedClient {

        @ParameterizedTest
        @ValueSource(strings = {
            "/api/v1/instance/properties",
            "/api/v1/auth/me",
            "/api/v1/tables",
            "/api/v1",
            "/api"})
        void shouldRefuseAPathThatHasNotBeenAllowed(String path) {
            given()
                    .when().get(path)
                    .then()
                    .statusCode(401);
        }

        @Test
        void shouldRefuseAPathThatHasNoResourceBehindIt() {
            given()
                    .when().get("/api/v1/does-not-exist")
                    .then()
                    .statusCode(401);
        }

        @Test
        void shouldServeTheUserInterfaceAtTheRootRatherThanRefuseIt() {
            given()
                    .when().get("/")
                    .then()
                    .statusCode(200);
        }

        @Test
        void shouldRefuseAWriteToAPathThatIsReadableWithoutAuthenticating() {
            given()
                    .when().post("/api/v1/auth/info")
                    .then()
                    .statusCode(401);
        }
    }

    @Nested
    @DisplayName("Allow clients to access paths that return information required to authenticate")
    class AllowAuthenticationPrerequisites {

        @Test
        void shouldReportBeingHealthyWithoutAuthenticating() {
            given()
                    .when().get("/q/health")
                    .then()
                    .statusCode(200)
                    .body("status", is("UP"));
        }

        @Test
        void shouldAllowAClientToAskHowToAuthenticateWithoutAuthenticating() {
            int status = given()
                    .when().get("/api/v1/auth/info")
                    .then()
                    .extract().statusCode();

            assertThat(status).isNotEqualTo(401);
        }

        @Test
        void shouldRefuseABrowserPreflightBecauseOfItsOriginRatherThanBecauseItHasNotAuthenticated() {
            given()
                    .header("Origin", "https://ui.internal")
                    .header("Access-Control-Request-Method", "GET")
                    .when().options("/api/v1/instance/properties")
                    .then()
                    .statusCode(403);
        }
    }

}
