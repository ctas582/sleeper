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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import sleeper.api.rest.testutils.LocalAwsTestResource;
import sleeper.core.properties.instance.InstanceProperties;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.instance;
import static sleeper.api.rest.testutils.SleeperInstanceForTest.saveProperties;
import static sleeper.core.properties.instance.ApiProperty.API_AUTH_ADMIN_PRINCIPALS;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.CONFIG_BUCKET;
import static sleeper.local.aws.test.SleeperLocalAwsClients.S3_CLIENT;

@QuarkusTest
@QuarkusTestResource(LocalAwsTestResource.class)
@TestProfile(InvalidConfigurationIT.PointAtABucketWithInvalidConfiguration.class)
class InvalidConfigurationIT {

    private static final String BUCKET = "a-bucket-with-invalid-configuration";
    private static final String INVALID_VALUE = "a-value-that-must-not-be-shown";

    public static class PointAtABucketWithInvalidConfiguration implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("sleeper.config.bucket", BUCKET);
        }
    }

    @BeforeEach
    void setUp() {
        try {
            S3_CLIENT.createBucket(builder -> builder.bucket(BUCKET));
        } catch (BucketAlreadyOwnedByYouException e) {
            // Another test in this class created it already.
        }
        InstanceProperties properties = instance();
        properties.set(CONFIG_BUCKET, BUCKET);
        properties.set(API_AUTH_ADMIN_PRINCIPALS, INVALID_VALUE);
        saveProperties(properties);
    }

    @Test
    void shouldSayWhichPropertyIsInvalidRatherThanThatTheBucketCouldNotBeRead() {
        given()
                .when().get("/api/v1/auth/info")
                .then()
                .statusCode(503)
                .body("error", is("instance_configuration_invalid"))
                .body("message", containsString(API_AUTH_ADMIN_PRINCIPALS.getPropertyName()))
                .body("remediation", containsString("Correct these properties"));
    }

    @Test
    void shouldNotShowTheValueOfAnInvalidProperty() {
        String body = given().when().get("/api/v1/auth/info").then().statusCode(503).extract().asString();
        assertThat(body).doesNotContain(INVALID_VALUE);
    }

}
