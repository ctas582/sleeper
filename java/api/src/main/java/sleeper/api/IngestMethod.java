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
package sleeper.api;

import org.apache.commons.lang3.EnumUtils;

import sleeper.bulkimport.core.configuration.BulkImportPlatform;
import sleeper.core.properties.model.OptionalStack;

import java.util.Locale;
import java.util.Optional;

public enum IngestMethod {

    INGEST_BATCHER(OptionalStack.IngestBatcherStack),
    STANDARD_INGEST(OptionalStack.IngestStack),
    BULK_IMPORT_EKS(BulkImportPlatform.EKS),
    BULK_IMPORT_EMR_SERVERLESS(BulkImportPlatform.EMRServerless),
    BULK_IMPORT_EMR(BulkImportPlatform.NonPersistentEMR),
    BULK_IMPORT_PERSISTENT_EMR(BulkImportPlatform.PersistentEMR);

    private final OptionalStack optionalStack;
    private final BulkImportPlatform bulkImportPlatform;

    IngestMethod(OptionalStack optionalStack) {
        this.optionalStack = optionalStack;
        this.bulkImportPlatform = null;
    }

    IngestMethod(BulkImportPlatform bulkImportPlatform) {
        this.optionalStack = bulkImportPlatform.getOptionalStack();
        this.bulkImportPlatform = bulkImportPlatform;
    }

    public OptionalStack getOptionalStack() {
        return optionalStack;
    }

    public Optional<BulkImportPlatform> getBulkImportPlatform() {
        return Optional.ofNullable(bulkImportPlatform);
    }

    public String getWireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static IngestMethod fromWireName(String wireName) {
        IngestMethod method = EnumUtils.getEnumIgnoreCase(IngestMethod.class, wireName);
        if (method == null) {
            throw new IllegalArgumentException("Unsupported ingest method: " + wireName);
        }
        return method;
    }

}