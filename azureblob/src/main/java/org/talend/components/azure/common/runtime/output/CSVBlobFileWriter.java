/*
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.talend.components.azure.common.runtime.output;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.talend.components.azure.common.csv.CSVFormatOptions;
import org.talend.components.azure.output.BlobOutputConfiguration;
import org.talend.components.azure.service.AzureBlobConnectionServices;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;

import com.microsoft.azure.storage.StorageException;

public class CSVBlobFileWriter extends BlobFileWriter {

    private final CSVFormatOptions configCSV;

    private boolean fileIsEmpty = true;

    public CSVBlobFileWriter(BlobOutputConfiguration config, AzureBlobConnectionServices connectionServices) throws Exception {
        super(config, connectionServices);
        this.configCSV = config.getDataset().getCsvOptions();

    }

    // TODO move common implementation to abstract class
    @Override
    public void flush() throws IOException, StorageException {
        if (getBatch().isEmpty())
            return;

        String content = convertBatchToString();
        if (!fileIsEmpty) {
            content = configCSV.getRecordDelimiter().getDelimiterValue() + content;
        } else if (configCSV.isUseHeader() && configCSV.getHeader() > 0) {
            appendHeader();
        }
        getBlobItem().appendText(content, "UTF-8", null, null, AzureBlobConnectionServices.getTalendOperationContext());
        fileIsEmpty = false;
        // TODO charset name

        getBatch().clear();
    }

    private void appendHeader() throws IOException, StorageException {
        // TODO add more lines if needed
        if (getSchema() == null || getSchema().getEntries().size() == 0)
            return;
        StringBuilder headerBuilder = new StringBuilder();
        for (int i = 0; i < configCSV.getHeader() - 1; i++) {
            headerBuilder.append("//header line").append(configCSV.getRecordDelimiter().getDelimiterValue());
        }

        headerBuilder.append(getSchema().getEntries().get(0).getName());
        for (int i = 1; i < getSchema().getEntries().size(); i++) {
            headerBuilder.append(configCSV.getFieldDelimiter().getDelimiterValue())
                    .append(getSchema().getEntries().get(i).getName());
        }
        getBlobItem().appendText(headerBuilder.toString() + configCSV.getRecordDelimiter().getDelimiterValue());
        fileIsEmpty = false;
    }

    private String convertBatchToString() {
        StringBuilder contentBuilder = new StringBuilder();
        List<Record> batch = getBatch();
        Iterator<Record> recordIterator = batch.iterator();
        if (recordIterator.hasNext()) {
            contentBuilder.append(convertRecordToString(recordIterator.next()));

            while (recordIterator.hasNext()) {
                contentBuilder.append(configCSV.getRecordDelimiter().getDelimiterValue())
                        .append(convertRecordToString(recordIterator.next()));
            }
        }

        return contentBuilder.toString();
    }

    // TODO generic?? and move it to some converter-library
    private /* T */String convertRecordToString(Record record) {
        StringBuilder stringBuilder = new StringBuilder();

        if (!getSchema().getEntries().isEmpty()) {
            stringBuilder.append(record.get(Object.class, getSchema().getEntries().get(0).getName()));

            for (int i = 1; i < getSchema().getEntries().size(); i++) {
                stringBuilder.append(configCSV.getFieldDelimiter().getDelimiterValue())
                        .append(record.get(Object.class, getSchema().getEntries().get(i).getName()));
            }
        }

        return stringBuilder.toString();
    }
}
