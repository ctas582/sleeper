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
package sleeper.cdk.artefacts.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ecr.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ecr.model.ImageDetail;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;

import sleeper.core.properties.instance.InstanceProperties;
import sleeper.core.properties.model.ArtefactsMode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.VERSION;
import static sleeper.core.properties.instance.CommonProperty.ARTEFACTS_MODE;

/**
 * Finds container images to deploy. Looks up the latest digest for each image in an ECR repository. The
 * deployment will be done against a specific digest of each image. It will only check the repository once for each
 * image, and you can reuse the same object for multiple Sleeper instances.
 */
public class SleeperContainerImageDigestProvider {

    public static final Logger LOGGER = LoggerFactory.getLogger(SleeperContainerImageDigestProvider.class);

    private final GetDigest getDigest;
    private final Map<String, String> latestDigestByImageName = new HashMap<>();

    public SleeperContainerImageDigestProvider(GetDigest getDigest) {
        this.getDigest = getDigest;
    }

    /**
     * Creates a provider that looks up the latest digest for each image in an ECR repository. The deployment will be
     * done against a specific digest of each image. It will only check the repository once for each image, and you can
     * reuse the same object for multiple Sleeper instances.
     *
     * @param  ecrClient          the ECR client
     * @param  instanceProperties the instance properties
     * @return                    an image digest provider
     */
    public static SleeperContainerImageDigestProvider from(EcrClient ecrClient, InstanceProperties instanceProperties) {
        if (isPublishedMode(instanceProperties)) {
            LOGGER.info("Artefacts mode is published; pinning ECR image deployments to tag {} instead of digest",
                    instanceProperties.get(VERSION));
            return new SleeperContainerImageDigestProvider(GetDigest.fromVersionTag(instanceProperties));
        }
        return new SleeperContainerImageDigestProvider(GetDigest.fromEcrRepository(ecrClient, instanceProperties));
    }

    private static boolean isPublishedMode(InstanceProperties instanceProperties) {
        String mode = instanceProperties.get(ARTEFACTS_MODE);
        return mode != null && ArtefactsMode.PUBLISHED.name().equalsIgnoreCase(mode.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Get the digest of the Docker image.
     *
     * @param  imageName         the name of the image
     * @param  ecrRepositoryName the name of the ECR repository
     * @return                   the digest for the given deployment
     */
    public String getDigestToDeploy(String imageName, String ecrRepositoryName) {
        return latestDigestByImageName.computeIfAbsent(imageName, name -> getDigest.getDigest(imageName, ecrRepositoryName));
    }

    /**
     * Checks the digest for the latest version of a given image in an ECR repository. When we provide the CDK with a
     * specific image digest, it can tell when the image has changed even if it still has the same name.
     */
    public interface GetDigest {
        /**
         * Get the digest of the Docker image.
         *
         * @param  imageName         the name of the image
         * @param  ecrRepositoryName the name of the ECR repository
         * @return                   the digest for the given deployment
         */
        String getDigest(String imageName, String ecrRepositoryName);

        /**
         * Implementation of GetDigest that checks the digest for the latest version of a given image in an ECR
         * repository.
         *
         * @param  ecrClient          the ECR client
         * @param  instanceProperties the instance properties
         * @return                    the get digest implementation
         */
        static GetDigest fromEcrRepository(EcrClient ecrClient, InstanceProperties instanceProperties) {
            return (imageName, ecrRepositoryName) -> {
                LOGGER.info("Checking latest digest for image: {}", imageName);

                DescribeImagesResponse response = ecrClient.describeImages(DescribeImagesRequest.builder()
                        .repositoryName(ecrRepositoryName)
                        .imageIds(ImageIdentifier.builder().imageTag(instanceProperties.get(VERSION)).build())
                        .build());

                String digest = response.imageDetails().stream()
                        .findFirst()
                        .map(ImageDetail::imageDigest)
                        .orElseThrow(() -> new RuntimeException("No image digest found!"));
                LOGGER.info("Found latest digest for image {}: {}", imageName, digest);
                return digest;
            };
        }

        /**
         * A GetDigest implementation that returns the configured Sleeper version as the tag or digest to deploy.
         * Used when artefacts mode is published: the ECR image is expected to be immutable for a given version tag,
         * and the digest lookup may not be possible (e.g. cross-account with no read access from CDK synth).
         *
         * @param  instanceProperties the instance properties
         * @return                    the get digest implementation
         */
        static GetDigest fromVersionTag(InstanceProperties instanceProperties) {
            return (imageName, ecrRepositoryName) -> instanceProperties.get(VERSION);
        }
    }

}
