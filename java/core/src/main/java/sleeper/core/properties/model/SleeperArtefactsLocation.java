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
package sleeper.core.properties.model;

/**
 * Helpers to generate the name of AWS resources containing Sleeper deployment artefacts.
 */
public class SleeperArtefactsLocation {

    private SleeperArtefactsLocation() {
    }

    /**
     * Computes the default jars bucket name. See the instance property `sleeper.jars.bucket`.
     *
     * @param  accountName           the AWS account name
     * @param  artefactsDeploymentId the deployment ID
     * @return                       the bucket name
     */
    public static String getDefaultJarsBucketName(String accountName, String artefactsDeploymentId) {
        return "sleeper-" + artefactsDeploymentId + "-jars-" + accountName;
    }

    /**
     * Computes the default ECR repository prefix. See the instance property `sleeper.ecr.repository.prefix`.
     *
     * @param  artefactsDeploymentId the deployment ID
     * @return                       the repository prefix
     */
    public static String getDefaultEcrRepositoryPrefix(String artefactsDeploymentId) {
        return artefactsDeploymentId;
    }

    /**
     * Combines a base name with an optional artefacts prefix. Used to compose the S3 key of a jar file and the
     * ECR repository name for a Docker image so that different tagged releases or branches can coexist in the same
     * bucket or ECR namespace.
     *
     * @param  prefix   the artefacts prefix (may be null or empty)
     * @param  baseName the resource name relative to the prefix
     * @return          the prefix and base name joined with a slash, or just the base name if the prefix is empty
     */
    public static String applyPrefix(String prefix, String baseName) {
        String normalised = normalisePrefix(prefix);
        if (normalised.isEmpty()) {
            return baseName;
        }
        return normalised + "/" + baseName;
    }

    /**
     * Normalises an artefacts prefix by trimming whitespace and removing leading or trailing slashes.
     *
     * @param  prefix the value to normalise (may be null)
     * @return        the normalised prefix, or an empty string if the value was null or empty
     */
    public static String normalisePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String trimmed = prefix.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

}
