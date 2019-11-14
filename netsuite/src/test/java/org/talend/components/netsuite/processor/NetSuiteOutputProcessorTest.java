/*
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
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
package org.talend.components.netsuite.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.talend.components.netsuite.NetSuiteBaseTest;
import org.talend.components.netsuite.dataset.NetSuiteDataSet;
import org.talend.components.netsuite.dataset.NetSuiteInputProperties;
import org.talend.components.netsuite.dataset.NetSuiteOutputProperties;
import org.talend.components.netsuite.dataset.NetSuiteOutputProperties.DataAction;
import org.talend.components.netsuite.dataset.SearchConditionConfiguration;
import org.talend.components.netsuite.source.NsObjectInputTransducer;
import org.talend.components.netsuite.test.TestCollector;
import org.talend.components.netsuite.test.TestEmitter;
import org.talend.components.netsuite.utils.SampleData;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.junit5.WithComponents;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithComponents("org.talend.components.netsuite")
public class NetSuiteOutputProcessorTest extends NetSuiteBaseTest {

    @BeforeEach
    void setup() {
        TestEmitter.reset();
        TestCollector.reset();
    }

    @Test
    void testCreateUpdateDeleteAccountRecordType() {
        NetSuiteOutputProperties outputProperties = createOutputProperties();
        NetSuiteDataSet dataSet = outputProperties.getDataSet();
        dataSet.setRecordType("Account");
        // add record
        outputProperties.setAction(DataAction.ADD);
        List<String> schemaFields = Arrays.asList("SubsidiaryList", "Description", "AcctName", "AcctType", "InternalId",
                "ExternalId");
        NsObjectInputTransducer inputTransducer = new NsObjectInputTransducer(clientService, messages, factory,
                service.getSchema(dataSet, schemaFields), "Account", "2018.2");
        Record record = inputTransducer.read(() -> SampleData.prepareAccountRecord(null));
        buildAndRunCollectorJob(outputProperties, Collections.singletonList(record));

        // read the record
        NetSuiteInputProperties inputDataSet = new NetSuiteInputProperties();
        inputDataSet.setDataSet(dataSet);
        final Record insertedRecord = buildAndRunEmitterJob(inputDataSet).stream()
                .filter(r -> r.getString("AcctName").equals(record.getString("AcctName"))).findFirst()
                .orElseThrow(IllegalStateException::new);
        TestCollector.reset();

        // update the record
        Record updateRecord = inputTransducer.read(() -> SampleData.prepareAccountRecord(insertedRecord));
        outputProperties.setAction(DataAction.UPDATE);
        buildAndRunCollectorJob(outputProperties, Collections.singletonList(updateRecord));

        // reread the updated record
        inputDataSet.setSearchCondition(createSearchConditionConfiguration(updateRecord.getString("InternalId")));
        Record resultUpdatedRecord = buildAndRunEmitterJob(inputDataSet).get(0);
        assertEquals(updateRecord.getString("Description"), resultUpdatedRecord.getString("Description"));

        // delete the record
        outputProperties.setAction(DataAction.DELETE);
        buildAndRunCollectorJob(outputProperties, Collections.singletonList(updateRecord));
        assertTrue(buildAndRunEmitterJob(inputDataSet).isEmpty());
    }

    @Test
    void testNativeUpsert() throws IOException {
        createUpsertCustomRecord(true);
    }

    @Test
    void testCustomUpsert() throws IOException {
        createUpsertCustomRecord(false);
    }

    private void createUpsertCustomRecord(boolean isNativeUpsert) throws IOException {
        NetSuiteOutputProperties outputProperties = createOutputProperties();
        NetSuiteDataSet dataSet = outputProperties.getDataSet();
        clientService.getMetaDataSource().setCustomizationEnabled(true);
        dataSet.setRecordType("customrecordqacomp_custom_recordtype");
        List<String> schemaFields = Arrays.asList("Name", "Custrecord79", "Custrecord80", "InternalId", "ExternalId");
        outputProperties.setUseNativeUpsert(isNativeUpsert);
        NsObjectInputTransducer inputTransducer = new NsObjectInputTransducer(clientService, messages, factory,
                service.getSchema(dataSet, schemaFields), "customrecordqacomp_custom_recordtype", "2018.2");
        NetSuiteInputProperties inputDataSet = new NetSuiteInputProperties();
        inputDataSet.setDataSet(dataSet);

        Record record = null;
        Record updateRecord = null;
        if (isNativeUpsert) {
            record = inputTransducer.read(() -> SampleData.prepareCustomRecord(null));
            final String preparedCustomField79Value = record.getString("Custrecord79");
            outputProperties.setAction(DataAction.ADD);
            buildAndRunCollectorJob(outputProperties, Collections.singletonList(record));

            inputDataSet.setSearchCondition(Collections.singletonList(
                    new SearchConditionConfiguration("Custrecord79", "String.is", preparedCustomField79Value, "")));
            Record finalRecord = buildAndRunEmitterJob(inputDataSet).stream().findFirst().orElseThrow(IllegalStateException::new);
            updateRecord = inputTransducer.read(() -> SampleData.prepareCustomRecord(finalRecord));
            record = finalRecord;
        } else {
            updateRecord = inputTransducer.read(() -> SampleData.prepareCustomRecord(null));
        }

        outputProperties.setAction(DataAction.UPSERT);
        buildAndRunCollectorJob(outputProperties, Collections.singletonList(updateRecord));

        final String preparedCustomField80Value = updateRecord.getString("Custrecord80");
        inputDataSet.setSearchCondition(Collections
                .singletonList(new SearchConditionConfiguration("Custrecord80", "String.is", preparedCustomField80Value, "")));
        Record resultUpdatedRecord = buildAndRunEmitterJob(inputDataSet).stream().findFirst()
                .orElseThrow(IllegalStateException::new);

        assertEquals(updateRecord.getString("Name"), resultUpdatedRecord.getString("Name"));

        outputProperties.setAction(DataAction.DELETE);
        if (isNativeUpsert) {
            TestEmitter.addRecord(record);
        }

        buildAndRunCollectorJob(outputProperties, Collections.singletonList(resultUpdatedRecord));
    }

    @Test
    void testCreateVendorBillWithTransactionField() {
        NetSuiteOutputProperties outputProperties = createOutputProperties();
        NetSuiteDataSet dataSet = outputProperties.getDataSet();
        outputProperties.setAction(DataAction.ADD);
        clientService.getMetaDataSource().setCustomizationEnabled(true);
        dataSet.setRecordType("PurchaseOrder");
        List<String> schemaFields = Arrays.asList("Custbody_clarivates_custom", "Custbody111", "Subsidiary", "ItemList",
                "Message", "CustomForm", "Entity", "ExchangeRate", "SupervisorApproval", "InternalId", "ExternalId");

        NsObjectInputTransducer inputTransducer = new NsObjectInputTransducer(clientService, messages, factory,
                service.getSchema(dataSet, schemaFields), "PurchaseOrder", "2018.2");

        Record record = inputTransducer.read(SampleData::preparePurchaseOrder);

        buildAndRunCollectorJob(outputProperties, Collections.singletonList(record));

        NetSuiteInputProperties inputDataSet = new NetSuiteInputProperties();
        inputDataSet.setDataSet(dataSet);

        String messageStringPrepared = record.getString("Message");
        inputDataSet.setSearchCondition(
                Collections.singletonList(new SearchConditionConfiguration("Message", "String.is", messageStringPrepared, "")));
        Record resultRecord = buildAndRunEmitterJob(inputDataSet).stream()
                // .filter(r -> messageStringPrepared.equals(r.getString("Message")))
                .findFirst().orElseThrow(IllegalStateException::new);
        assertEquals(record.getString("Custbody111"), resultRecord.getString("Custbody111"));

        outputProperties.setAction(DataAction.DELETE);
        buildAndRunCollectorJob(outputProperties, Collections.singletonList(resultRecord));

        inputDataSet.setSearchCondition(createSearchConditionConfiguration(resultRecord.getString("InternalId")));
        assertTrue(buildAndRunEmitterJob(inputDataSet).isEmpty());
    }
}