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

import org.apache.commons.lang3.EnumUtils;

/**
 * How Sleeper deployment artefacts (jars and Docker images) are sourced.
 * BUILD (default) means the deploy client will build and upload artefacts on every deployment.
 * PUBLISHED means the artefacts have already been published into the referenced S3 bucket and ECR repositories,
 * and the deploy client should skip building or uploading them.
 */
public enum ArtefactsMode {

    BUILD, PUBLISHED;

    /**
     * Checks if the value is a valid artefacts mode.
     *
     * @param  value the value
     * @return       true if it is valid
     */
    public static boolean isValid(String value) {
        return EnumUtils.isValidEnumIgnoreCase(ArtefactsMode.class, value);
    }
}
