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

/**
 * Why the API could not do what was asked.
 *
 * @param error       a code identifying what went wrong, for a client to branch on
 * @param message     what went wrong
 * @param remediation what to do about it
 */
public record ApiError(String error, String message, String remediation) {

    /**
     * Describes a problem with no remediation.
     *
     * @param  error   the code identifying what went wrong
     * @param  message what went wrong
     * @return         the error
     */
    public static ApiError of(String error, String message) {
        return new ApiError(error, message, null);
    }

}
