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
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import sleeper.api.rest.testutils.LocalStackTestResource;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(LocalStackTestResource.class)
@TestProfile(UnreadableConfigurationIT.PointAtABucketThatDoesNotExist.class)
class UnreadableConfigurationIT {

    private static final String MISSING_BUCKET = "a-bucket-that-does-not-exist";

    /**
     * Points the API at a bucket that was never created.
     */
    public static class PointAtABucketThatDoesNotExist implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("sleeper.config.bucket", MISSING_BUCKET);
        }
    }

    @Test
    void shouldSayTheConfigurationCouldNotBeReadRatherThanFailing() {
        given()
                .when().get("/api/v1/auth/info")
                .then()
                .statusCode(503)
                .body("error", is("instance_configuration_unreadable"))
                .body("message", containsString(MISSING_BUCKET))
                .body("remediation", containsString("Check the bucket exists"));
    }

    @Test
    void shouldNotReportWhatWentWrongInsideAws() {
        // What failed in S3 is for whoever runs the instance to see in the logs, not for whoever called the API.
        String body = given().when().get("/api/v1/auth/info").then().statusCode(503).extract().asString();

        assertThat(body)
                .doesNotContain("NoSuchBucket")
                .doesNotContain("software.amazon.awssdk")
                .doesNotContain("stack");
    }

}
