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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Checks that the user interface is served to a client who has not signed in.
 */
@QuarkusTest
class UserInterfaceTest {

    @Nested
    @DisplayName("Serve the interface to a client who has not signed in")
    class ServeInterface {
        @ParameterizedTest
        @ValueSource(strings = {"/", "/index.html"})
        void shouldServeTheInterfaceWithoutAuthenticating(String path) {
            given()
                    .when().get(path)
                    .then()
                    .statusCode(200);
        }

        @Test
        void shouldServeTheIconRatherThanTheInterface() {
            given()
                    .when().get("/favicon.ico")
                    .then()
                    .statusCode(200)
                    .contentType(not(containsString("html")));
        }
    }

    @Nested
    @DisplayName("Serve a page within the interface that the browser asked for directly")
    class ServePageWithinInterface {

        @ParameterizedTest
        @ValueSource(strings = {"/tables", "/tables/some-table", "/queries/abc123"})
        void shouldServeTheInterfaceForAPageWithinIt(String path) {
            given()
                    .when().get(path)
                    .then()
                    .statusCode(200);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "/api/v1/instance/properties",
            "/api/v1/auth/me",
            "/api/v1/does-not-exist",
            "/api"})
        void shouldLeaveTheApiAlone(String path) {
            given()
                    .when().get(path)
                    .then()
                    .statusCode(401);
        }

        @Test
        void shouldLeaveTheHealthChecksAlone() {
            given()
                    .when().get("/q/health")
                    .then()
                    .statusCode(200)
                    .body("status", is("UP"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"/anything-at-all", "/some/deep/link"})
        void shouldServeTheInterfaceForAnyPathThatIsNotTheServersOwn(String path) {
            given()
                    .when().get(path)
                    .then()
                    .statusCode(200);
        }

        @Test
        void shouldNotServeTheInterfaceForAFileThatIsNotThere() {
            given()
                    .when().get("/assets/not-a-real-file.js")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("Serve the interface to a browser reaching the server through API Gateway")
    class ServeThroughApiGateway {

        // API Gateway terminates the browser's HTTPS connection and invokes the lambda with the request as an
        // event. Quarkus replays that event to itself over an internal connection with no TLS. If the X-Forwarded-*
        // headers added by API Gateway aren't trusted then then CORS filter would reject the requests for any
        // UI assets (e.g. JavaScript, CSS).
        private static final String HOST = "abc123.execute-api.eu-west-2.amazonaws.com";
        private static final String ORIGIN = "https://" + HOST;

        @ParameterizedTest
        @ValueSource(strings = {"/", "/index.html", "/q/health"})
        void shouldAllowARequestFromTheSameWebsite(String path) {
            given()
                    .header("Host", HOST)
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Port", "443")
                    .header("Origin", ORIGIN)
                    .when().get(path)
                    .then()
                    .statusCode(200);
        }

        @Test
        void shouldStillRefuseARequestFromAnotherWebsite() {
            given()
                    .header("Host", HOST)
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Port", "443")
                    .header("Origin", "https://somewhere.else")
                    .when().get("/")
                    .then()
                    .statusCode(403);
        }
    }

    @Nested
    @DisplayName("Refuse everything else to a client who has not signed in")
    class RefuseEverythingElse {

        @ParameterizedTest
        @ValueSource(strings = {
            "/api/v1/instance/properties",
            "/api/v1/auth/me"})
        void shouldStillRefuseTheApi(String path) {
            given()
                    .when().get(path)
                    .then()
                    .statusCode(401);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "/assets/../api/v1/instance/properties",
            "/./api/v1/instance/properties"})
        void shouldRefuseAPathDressedUpToLookLikePartOfTheInterface(String path) {
            given()
                    .urlEncodingEnabled(false)
                    .when().get(path)
                    .then()
                    .statusCode(401);
        }

        @Test
        void shouldRefuseAWriteToAPathTheInterfaceIsServedFrom() {
            given()
                    .when().post("/index.html")
                    .then()
                    .statusCode(401);
        }
    }

}
