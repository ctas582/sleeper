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
import static org.hamcrest.Matchers.is;

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
