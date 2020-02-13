/*
 * Copyright (C) 2006-2020 Talend Inc. - www.talend.com
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
package org.talend.components.dynamicscrm.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.talend.sdk.component.junit.SimpleFactory.configurationByExample;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.talend.components.dynamicscrm.DynamicsCrmTestBase;
import org.talend.components.dynamicscrm.dataset.DynamicsCrmDataset;
import org.talend.components.dynamicscrm.output.DynamicsCrmOutputConfiguration.Action;
import org.talend.ms.crm.odata.ClientConfiguration;
import org.talend.ms.crm.odata.ClientConfiguration.WebAppPermission;
import org.talend.ms.crm.odata.ClientConfigurationFactory;
import org.talend.ms.crm.odata.DynamicsCRMClient;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.Schema.Type;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.manager.chain.Job;

import javax.naming.AuthenticationException;
import javax.naming.ServiceUnavailableException;

@WithComponents("org.talend.components.dynamicscrm")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DynamicsCrmOutputTestIT extends DynamicsCrmTestBase {

    private DynamicsCRMClient client;

    @BeforeAll
    public void init() throws AuthenticationException {
        ClientConfiguration clientConfig = ClientConfigurationFactory.buildOAuthWebClientConfiguration(getClientId(),
                getClientSecret(), getUsername(), getPassword(), authEndpoint, WebAppPermission.DELEGATED);
        clientConfig.setTimeout(60);
        clientConfig.setMaxRetry(5, 1000);
        clientConfig.setReuseHttpClient(false);

        client = new DynamicsCRMClient(clientConfig, rootUrl, entitySet);
    }

    @Test
    public void testInsert() {
        Record testRecord = createTestRecord();
        final DynamicsCrmDataset dataset = createDataset();
        final DynamicsCrmOutputConfiguration configuration = new DynamicsCrmOutputConfiguration();
        configuration.setDataset(dataset);
        configuration.setIgnoreNull(true);
        configuration.setEmptyStringToNull(true);
        configuration.setAction(Action.INSERT);
        configuration.setColumns(Arrays.asList("annualincome", "assistantname", "business2", "callback", "childrensnames",
                "company", "creditonhold"));

        final String config = configurationByExample().forInstance(configuration).configured().toQueryString();
        List<Record> testRecords = Collections.singletonList(testRecord);
        components.setInputData(testRecords);
        Job.components() //
                .component("in", "test://emitter") //
                .component("out", "DynamicsCrm://DynamicsCrmOutput?" + config) //
                .connections() //
                .from("in") //
                .to("out") //
                .build().run();

        List<ClientEntity> data = getData(client);
        assertEquals(1, data.size());

        ClientEntity entity = data.get(0);
        assertEquals(false, entity.getProperty("creditonhold").getPrimitiveValue().toValue());
        assertEquals(2.0f, ((BigDecimal) entity.getProperty("annualincome").getPrimitiveValue().toValue()).floatValue());
        assertEquals("assistant", entity.getProperty("assistantname").getPrimitiveValue().toString());
        assertEquals("business2", entity.getProperty("business2").getPrimitiveValue().toString());
        assertEquals("callback", entity.getProperty("callback").getPrimitiveValue().toString());
        assertEquals("childrensnames", entity.getProperty("childrensnames").getPrimitiveValue().toString());
        assertEquals(company, entity.getProperty("company").getPrimitiveValue().toString());
    }

    @Test
    public void testUpdate() throws ServiceUnavailableException {
        // insert data with 1.5 annualincome value, and after that we will update it with default test value(2.0)
        ClientEntity entity = client.newEntity();
        client.addEntityProperty(entity, "annualincome", EdmPrimitiveTypeKind.Decimal, 1.5);
        client.addEntityProperty(entity, "assistantname", EdmPrimitiveTypeKind.String, "assistant");
        client.addEntityProperty(entity, "business2", EdmPrimitiveTypeKind.String, "business2");
        client.addEntityProperty(entity, "callback", EdmPrimitiveTypeKind.String, "callback");
        client.addEntityProperty(entity, "childrensnames", EdmPrimitiveTypeKind.String, "childrensnames");
        client.addEntityProperty(entity, "company", EdmPrimitiveTypeKind.String, company);
        client.addEntityProperty(entity, "creditonhold", EdmPrimitiveTypeKind.Boolean, false);
        client.insertEntity(entity);

        // we need id for update. thus we need to load entity from CRM.
        ClientEntity testEntity = getData(client).get(0);
        String contactId = testEntity.getProperty("contactid").getPrimitiveValue().toString();
        Record testRecord = createTestRecordWithId(contactId);

        final DynamicsCrmDataset dataset = createDataset();
        final DynamicsCrmOutputConfiguration configuration = new DynamicsCrmOutputConfiguration();
        configuration.setDataset(dataset);
        configuration.setIgnoreNull(true);
        configuration.setEmptyStringToNull(true);
        configuration.setAction(Action.UPDATE);
        configuration.setColumns(Arrays.asList("annualincome", "assistantname", "business2", "callback", "childrensnames",
                "company", "creditonhold"));

        final String config = configurationByExample().forInstance(configuration).configured().toQueryString();
        List<Record> testRecords = Collections.singletonList(testRecord);
        components.setInputData(testRecords);
        Job.components() //
                .component("in", "test://emitter") //
                .component("out", "DynamicsCrm://DynamicsCrmOutput?" + config) //
                .connections() //
                .from("in") //
                .to("out") //
                .build().run();

        List<ClientEntity> data = getData(client);
        assertEquals(1, data.size());

        ClientEntity resultEntity = data.get(0);
        assertEquals(false, resultEntity.getProperty("creditonhold").getPrimitiveValue().toValue());
        assertEquals(2.0f, ((BigDecimal) resultEntity.getProperty("annualincome").getPrimitiveValue().toValue()).floatValue());
        assertEquals("assistant", resultEntity.getProperty("assistantname").getPrimitiveValue().toString());
        assertEquals("business2", resultEntity.getProperty("business2").getPrimitiveValue().toString());
        assertEquals("callback", resultEntity.getProperty("callback").getPrimitiveValue().toString());
        assertEquals("childrensnames", resultEntity.getProperty("childrensnames").getPrimitiveValue().toString());
        assertEquals(company, resultEntity.getProperty("company").getPrimitiveValue().toString());
    }

    @Test
    public void testDelete() throws ServiceUnavailableException {
        ClientEntity entity = createTestEntity(client);
        client.insertEntity(entity);

        // we need id for delete. thus we need to load entity from CRM.
        ClientEntity testEntity = getData(client).get(0);
        String contactId = testEntity.getProperty("contactid").getPrimitiveValue().toString();
        Record testRecord = createTestRecordWithId(contactId);

        final DynamicsCrmDataset dataset = createDataset();
        final DynamicsCrmOutputConfiguration configuration = new DynamicsCrmOutputConfiguration();
        configuration.setDataset(dataset);
        configuration.setIgnoreNull(true);
        configuration.setEmptyStringToNull(true);
        configuration.setAction(Action.DELETE);
        configuration.setColumns(Arrays.asList("annualincome", "assistantname", "business2", "callback", "childrensnames",
                "company", "creditonhold"));

        final String config = configurationByExample().forInstance(configuration).configured().toQueryString();
        List<Record> testRecords = Collections.singletonList(testRecord);
        components.setInputData(testRecords);
        Job.components() //
                .component("in", "test://emitter") //
                .component("out", "DynamicsCrm://DynamicsCrmOutput?" + config) //
                .connections() //
                .from("in") //
                .to("out") //
                .build().run();

        List<ClientEntity> data = getData(client);
        assertEquals(0, data.size());
    }

    @AfterEach
    public void clearData() throws ServiceUnavailableException {
        tearDown(client);
    }

    private Record createTestRecordWithId(String id) {
        Schema schema = builderFactory.newSchemaBuilder(Type.RECORD)
                .withEntry(builderFactory.newEntryBuilder().withName("annualincome").withType(Type.FLOAT)
                        .withElementSchema(builderFactory.newSchemaBuilder(Type.FLOAT).build()).build())
                .withEntry(builderFactory.newEntryBuilder().withName("contactid").withType(Type.STRING)
                        .withElementSchema(builderFactory.newSchemaBuilder(Type.STRING).build()).build())
                .withEntry(builderFactory.newEntryBuilder().withName("assistantname").withType(Type.STRING)
                        .withElementSchema(builderFactory.newSchemaBuilder(Type.STRING).build()).build())
                .withEntry(builderFactory.newEntryBuilder().withName("business2").withType(Type.STRING)
                        .withElementSchema(builderFactory.newSchemaBuilder(Type.STRING).build()).build())
                .withEntry(builderFactory.newEntryBuilder().withName("callback").withType(Type.STRING)
                        .withElementSchema(builderFactory.newSchemaBuilder(Type.STRING).build()).build())
                .withEntry(builderFactory.newEntryBuilder().withName("childrensnames").withType(Type.STRING)
                        .withElementSchema(builderFactory.newSchemaBuilder(Type.STRING).build()).build())
                .withEntry(builderFactory.newEntryBuilder().withName("company").withType(Type.STRING)
                        .withElementSchema(builderFactory.newSchemaBuilder(Type.STRING).build()).build())
                .withEntry(builderFactory.newEntryBuilder().withName("creditonhold").withType(Type.BOOLEAN)
                        .withElementSchema(builderFactory.newSchemaBuilder(Type.BOOLEAN).build()).build())
                .build();
        Record testRecord = builderFactory.newRecordBuilder(schema).withString("contactid", id).withFloat("annualincome", 2.0f)
                .withString("assistantname", "assistant").withString("business2", "business2").withString("callback", "callback")
                .withString("childrensnames", "childrensnames").withString("company", company).withBoolean("creditonhold", false)
                .build();
        return testRecord;
    }

}