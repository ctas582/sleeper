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
package sleeper.api.rest.config;

import sleeper.core.properties.SleeperPropertiesInvalidException;
import sleeper.core.properties.SleeperProperty;

import static java.util.stream.Collectors.joining;

/**
 * The configuration of the instance this API serves could not be used, either because it could not be read or
 * because it is not valid.
 */
public class InstanceConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final String remediation;

    private InstanceConfigurationException(String errorCode, String message, String remediation, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.remediation = remediation;
    }

    /**
     * Creates an exception for when the configuration could not be read.
     *
     * @param  bucket the bucket the configuration was read from
     * @param  cause  what went wrong
     * @return        the exception
     */
    public static InstanceConfigurationException unreadable(String bucket, Throwable cause) {
        return new InstanceConfigurationException("instance_configuration_unreadable",
                "The configuration of this Sleeper instance could not be read from " + bucket + ".",
                "Check the bucket exists, holds this instance's configuration, and that the API is allowed to read it.",
                cause);
    }

    /**
     * Creates an exception for when the configuration was read, but some properties are not valid. Names the
     * properties, but not their values.
     *
     * @param  bucket the bucket the configuration was read from
     * @param  cause  the validation failure
     * @return        the exception
     */
    public static InstanceConfigurationException invalid(String bucket, SleeperPropertiesInvalidException cause) {
        String properties = cause.getInvalidValues().keySet().stream()
                .map(SleeperProperty::getPropertyName)
                .sorted()
                .collect(joining(", "));
        return new InstanceConfigurationException("instance_configuration_invalid",
                "The configuration of this Sleeper instance in " + bucket + " is not valid. " +
                        "These properties are invalid: " + properties + ".",
                "Correct these properties in the instance configuration. The API's logs say why they are invalid.",
                cause);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getRemediation() {
        return remediation;
    }

}
