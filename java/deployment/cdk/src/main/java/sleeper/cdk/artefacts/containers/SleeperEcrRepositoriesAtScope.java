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

import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.constructs.Construct;

import sleeper.core.deploy.DockerDeployment;
import sleeper.core.deploy.LambdaJar;
import sleeper.core.properties.instance.InstanceProperties;

import java.util.HashMap;
import java.util.Map;

import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.ACCOUNT;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.PARTITION;
import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REGION;
import static sleeper.core.properties.instance.CommonProperty.ECR_REPOSITORY_ACCOUNT;
import static sleeper.core.properties.instance.CommonProperty.ECR_REPOSITORY_REGION;

public class SleeperEcrRepositoriesAtScope {
    private final Construct scope;
    private final InstanceProperties instanceProperties;
    private final Map<String, IRepository> imageNameToRepository = new HashMap<>();

    public SleeperEcrRepositoriesAtScope(Construct scope, InstanceProperties instanceProperties) {
        this.scope = scope;
        this.instanceProperties = instanceProperties;
    }

    public IRepository getRepository(LambdaJar jar) {
        return imageNameToRepository.computeIfAbsent(jar.getImageName(),
                imageName -> createRepositoryReference(jar.getImageName() + "-repository",
                        jar.getEcrRepositoryName(instanceProperties)));
    }

    public IRepository getRepository(DockerDeployment deployment) {
        return imageNameToRepository.computeIfAbsent(deployment.getDeploymentName(),
                imageName -> createRepositoryReference(deployment.getDeploymentName() + "-repository",
                        deployment.getEcrRepositoryName(instanceProperties)));
    }

    private IRepository createRepositoryReference(String id, String repositoryName) {
        String ecrAccount = instanceProperties.get(ECR_REPOSITORY_ACCOUNT);
        String ecrRegion = instanceProperties.get(ECR_REPOSITORY_REGION);
        if (ecrAccount == null && ecrRegion == null) {
            return Repository.fromRepositoryName(scope, id, repositoryName);
        }
        String account = ecrAccount != null ? ecrAccount : instanceProperties.get(ACCOUNT);
        String region = ecrRegion != null ? ecrRegion : instanceProperties.get(REGION);
        String partition = instanceProperties.get(PARTITION);
        String arn = "arn:" + partition + ":ecr:" + region + ":" + account + ":repository/" + repositoryName;
        return Repository.fromRepositoryArn(scope, id, arn);
    }

}
