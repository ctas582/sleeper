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
package sleeper.clients.deploy.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.RepositoryAlreadyExistsException;

import sleeper.core.deploy.DockerDeployment;
import sleeper.core.deploy.LambdaJar;
import sleeper.core.properties.instance.InstanceProperties;

import java.util.LinkedHashSet;
import java.util.Set;

import static sleeper.core.properties.instance.CommonProperty.ARTEFACTS_PREFIX;
import static sleeper.core.properties.instance.CommonProperty.ECR_REPOSITORY_PREFIX;

/**
 * A tool to create the ECR repositories a Sleeper release needs, matching how they are created by the CDK.
 * Mirrors {@link sleeper.cdk.artefacts.SleeperArtefactRepositories} but uses the ECR SDK directly so it can run without
 * bootstrapping CDK in the target account. Intended for use by the publish flow to ensure the repositories exist
 * before {@code docker push}. Idempotent: repositories that already exist are left as-is.
 */
public class EcrRepositoriesCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EcrRepositoriesCreator.class);

    // ECR-managed policy JSON granting EMR Serverless permission to pull images.
    // Matches the resource policy attached by SleeperArtefactRepositories for deployments with
    // isCreateEmrServerlessPolicy() == true.
    private static final String EMR_SERVERLESS_POLICY = "{"
            + "\"Version\":\"2012-10-17\","
            + "\"Statement\":[{"
            + "\"Effect\":\"Allow\","
            + "\"Principal\":{\"Service\":\"emr-serverless.amazonaws.com\"},"
            + "\"Action\":[\"ecr:BatchGetImage\",\"ecr:DescribeImages\",\"ecr:GetDownloadUrlForLayer\"]"
            + "}]}";

    // Lifecycle policy JSON matching the rules attached by SleeperArtefactRepositories:
    //   1. Delete untagged images after 1 day.
    //   2. Delete any image after 365 days.
    private static final String LIFECYCLE_POLICY = "{"
            + "\"rules\":["
            + "{"
            + "\"rulePriority\":1,"
            + "\"description\":\"Delete untagged images\","
            + "\"selection\":{"
            + "\"tagStatus\":\"untagged\","
            + "\"countType\":\"sinceImagePushed\","
            + "\"countUnit\":\"days\","
            + "\"countNumber\":1"
            + "},"
            + "\"action\":{\"type\":\"expire\"}"
            + "},"
            + "{"
            + "\"rulePriority\":2,"
            + "\"description\":\"Keep images for 365 days\","
            + "\"selection\":{"
            + "\"tagStatus\":\"any\","
            + "\"countType\":\"sinceImagePushed\","
            + "\"countUnit\":\"days\","
            + "\"countNumber\":365"
            + "},"
            + "\"action\":{\"type\":\"expire\"}"
            + "}"
            + "]}";

    private final EcrClient ecrClient;

    public EcrRepositoriesCreator(EcrClient ecrClient) {
        this.ecrClient = ecrClient;
    }

    /**
     * Entry point when invoked directly (e.g. from publishRelease.sh). Requires two arguments:
     * the ECR repository prefix and (optionally) the artefacts prefix.
     */
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: <ecr-repository-prefix> <optional-artefacts-prefix>");
            System.exit(1);
            return;
        }
        InstanceProperties instanceProperties = new InstanceProperties();
        instanceProperties.set(ECR_REPOSITORY_PREFIX, args[0]);
        if (args.length == 2 && args[1] != null && !args[1].isEmpty()) {
            instanceProperties.set(ARTEFACTS_PREFIX, args[1]);
        }
        try (EcrClient ecrClient = EcrClient.create()) {
            new EcrRepositoriesCreator(ecrClient).createAllForRelease(instanceProperties);
        }
    }

    /**
     * Creates one ECR repository per Sleeper Docker image and lambda-as-container image referenced by the release,
     * using the same naming as the CDK does. Repositories that already exist are logged and skipped.
     *
     * @param instanceProperties the instance properties (only sleeper.ecr.repository.prefix and sleeper.artefacts.prefix
     *                           are read)
     */
    public void createAllForRelease(InstanceProperties instanceProperties) {
        // Deduplicate by repository name — two entries could point at the same repo if configuration overlaps.
        Set<String> alreadyCreated = new LinkedHashSet<>();
        for (LambdaJar jar : LambdaJar.all()) {
            String repositoryName = jar.getEcrRepositoryName(instanceProperties);
            if (alreadyCreated.add(repositoryName)) {
                createRepository(repositoryName, false);
            }
        }
        for (DockerDeployment deployment : DockerDeployment.all()) {
            String repositoryName = deployment.getEcrRepositoryName(instanceProperties);
            if (alreadyCreated.add(repositoryName)) {
                createRepository(repositoryName, deployment.isCreateEmrServerlessPolicy());
            } else if (deployment.isCreateEmrServerlessPolicy()) {
                setEmrServerlessPolicy(repositoryName);
            }
        }
    }

    private void createRepository(String repositoryName, boolean emrServerlessPolicy) {
        boolean created = false;
        try {
            ecrClient.createRepository(builder -> builder.repositoryName(repositoryName));
            LOGGER.info("Created ECR repository {}", repositoryName);
            created = true;
        } catch (RepositoryAlreadyExistsException e) {
            LOGGER.info("ECR repository {} already exists, leaving as-is", repositoryName);
        }
        if (created) {
            setLifecyclePolicy(repositoryName);
        }
        if (emrServerlessPolicy) {
            setEmrServerlessPolicy(repositoryName);
        }
    }

    private void setLifecyclePolicy(String repositoryName) {
        ecrClient.putLifecyclePolicy(builder -> builder
                .repositoryName(repositoryName)
                .lifecyclePolicyText(LIFECYCLE_POLICY));
        LOGGER.info("Set lifecycle policy on ECR repository {}", repositoryName);
    }

    private void setEmrServerlessPolicy(String repositoryName) {
        ecrClient.setRepositoryPolicy(builder -> builder
                .repositoryName(repositoryName)
                .policyText(EMR_SERVERLESS_POLICY));
        LOGGER.info("Set EMR Serverless resource policy on ECR repository {}", repositoryName);
    }
}
