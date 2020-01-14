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
package org.talend.components.dynamicscrm.service;

import org.apache.olingo.client.api.communication.request.retrieve.EdmMetadataRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetRequest;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientProperty;
import org.apache.olingo.client.api.domain.ClientValue;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.constants.EdmTypeKind;
import org.apache.olingo.commons.core.edm.primitivetype.EdmPrimitiveTypeFactory;
import org.talend.components.dynamicscrm.datastore.DynamicsCrmConnection;
import org.talend.components.dynamicscrm.source.DynamicsCrmInputMapperConfiguration;
import org.talend.components.dynamicscrm.source.DynamicsCrmQueryResultsIterator;
import org.talend.ms.crm.odata.ClientConfiguration;
import org.talend.ms.crm.odata.ClientConfigurationFactory;
import org.talend.ms.crm.odata.DynamicsCRMClient;
import org.talend.ms.crm.odata.QueryOptionConfig;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import javax.naming.AuthenticationException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DynamicsCrmService {

    @Service
    private I18n i18n;

    public QueryOptionConfig createQueryOptionConfig(Schema schema, DynamicsCrmInputMapperConfiguration configuration) {
        QueryOptionConfig config = new QueryOptionConfig();
        final String[] names = schema.getEntries().stream().map(Schema.Entry::getName).collect(Collectors.toList())
                .toArray(new String[0]);
        config.setReturnEntityProperties(names);
        if (configuration.getFilter() != null && !configuration.getFilter().isEmpty()) {
            config.setFilter(configuration.getFilter());
        }
        return config;
    }

    public DynamicsCRMClient createClient(DynamicsCrmConnection connection, String entitySet) throws AuthenticationException {
        ClientConfiguration clientConfig_tMicrosoftCrmInput_1;
        if (connection.getAppType() == DynamicsCrmConnection.AppType.Native) {
            clientConfig_tMicrosoftCrmInput_1 = ClientConfigurationFactory.buildOAuthNativeClientConfiguration(
                    connection.getClientId(), connection.getUsername(), connection.getPassword(),
                    connection.getAuthorizationEndpoint());
        } else {
            clientConfig_tMicrosoftCrmInput_1 = ClientConfigurationFactory.buildOAuthWebClientConfiguration(
                    connection.getClientId(), connection.getClientSecret(), connection.getUsername(), connection.getPassword(),
                    connection.getAuthorizationEndpoint(), ClientConfiguration.WebAppPermission.DELEGATED);
        }
        clientConfig_tMicrosoftCrmInput_1.setTimeout(connection.getTimeout());
        clientConfig_tMicrosoftCrmInput_1.setMaxRetry(connection.getMaxRetries(), 1000);
        clientConfig_tMicrosoftCrmInput_1.setReuseHttpClient(false);
        DynamicsCRMClient client_tMicrosoftCrmInput_1 = new DynamicsCRMClient(clientConfig_tMicrosoftCrmInput_1,
                connection.getServiceRootUrl(), entitySet);
        return client_tMicrosoftCrmInput_1;
    }

    public List<String> getEntitySetNames(DynamicsCrmConnection connection) {
        try {
            DynamicsCRMClient client = createClient(connection, null);
            ODataEntitySetRequest<ClientEntitySet> request = client.createEndpointsNamesRequest();
            ODataRetrieveResponse<ClientEntitySet> response = request.execute();
            ClientEntitySet entitySet = response.getBody();
            List<String> entitySetNames = entitySet.getEntities().stream()
                    .map(e -> e.getProperty("name").getValue().asPrimitive().toString()).collect(Collectors.toList());
            return entitySetNames;
        } catch (AuthenticationException e) {
            throw new DynamicsCrmException(i18n.authenticationFailed(e.getMessage()));
        } catch (Exception e) {
            throw new DynamicsCrmException(i18n.entitySetRetrieveFailed(e.getMessage()));
        }
    }

    public DynamicsCrmQueryResultsIterator getEntitySetIterator(DynamicsCRMClient client, QueryOptionConfig config) {
        ODataEntitySetRequest<ClientEntitySet> request = client.createEntityRetrieveRequest(config);
        ODataRetrieveResponse<ClientEntitySet> response = request.execute();
        return new DynamicsCrmQueryResultsIterator(client, config, response.getBody());
    }

    public Schema getSchemaForEntitySet(DynamicsCRMClient client, String entitySetName, RecordBuilderFactory builderFactory) {
        EdmMetadataRequest metadataRequest = client.createMetadataRetrieveRequest();
        Edm metadata;
        try {
            ODataRetrieveResponse<Edm> metadataResponse = metadataRequest.execute();
            metadata = metadataResponse.getBody();
        } catch (Exception e) {
            throw new DynamicsCrmException(i18n.metadataRetrieveFailed(e.getMessage()));
        }
        EdmEntityContainer container = metadata.getEntityContainer();
        EdmEntitySet entitySet = container.getEntitySet(entitySetName);
        EdmEntityType type = entitySet.getEntityType();
        return parseSchema(metadata, type, builderFactory);
    }

    public Schema parseSchema(Edm edm, EdmStructuredType type, RecordBuilderFactory builderFactory) {
        Schema.Builder schemaBuilder = builderFactory.newSchemaBuilder(Schema.Type.RECORD);
        type.getPropertyNames().stream()
                .forEach(f -> schemaBuilder.withEntry(
                        builderFactory.newEntryBuilder().withName(f).withType(getTckType((EdmProperty) type.getProperty(f)))
                                .withElementSchema(getSubSchema(edm, (EdmProperty) type.getProperty(f), builderFactory))
                                .withNullable(((EdmProperty) type.getProperty(f)).isNullable()).build()));
        return schemaBuilder.build();
    }

    public Schema getSubSchema(Edm edm, EdmProperty edmElement, RecordBuilderFactory builderFactory) {
        if (edmElement.isPrimitive()) {
            return builderFactory.newSchemaBuilder(getTckType(edmElement)).build();
        }

        return parseSchema(edm, edm.getComplexType(edmElement.getType().getFullQualifiedName()), builderFactory);
    }

    protected Schema.Type getTckType(EdmProperty edmElement) {
        if (edmElement.isCollection()) {
            return Schema.Type.ARRAY;
        } else if (edmElement.getType().getKind() == EdmTypeKind.COMPLEX) {
            return Schema.Type.RECORD;
        }
        switch (edmElement.getType().getName()) {
        case "Edm.Boolean":
            return Schema.Type.BOOLEAN;
        case "Edm.Binary":
            return Schema.Type.BYTES;
        case "Edm.Byte":
        case "Edm.SByte":
        case "Edm.Int16":
        case "Edm.Int32":
            // Contains a date and time as an offset in minutes from GMT.
        case "Edm.DateTimeOffset":
            return Schema.Type.INT;
        case "Edm.Int64":
            return Schema.Type.LONG;
        case "Edm.DateTime":
        case "Edm.Time":
            return Schema.Type.DATETIME;
        case "Edm.Double":
            return Schema.Type.DOUBLE;
        case "Edm.Float":
        case "Edm.Decimal":
            return Schema.Type.FLOAT;
        case "Edm.Guid":
        case "Edm.String":
        default:
            return Schema.Type.STRING;
        }

    }

    public void convertToTckRecord(ClientEntity entity, Schema schema, Record.Builder rb, RecordBuilderFactory builderFactory) {
        for (Schema.Entry entry : schema.getEntries()) {
            ClientProperty property = entity.getProperty(entry.getName());
            if (property.hasCollectionValue()) {
                Iterator<ClientValue> valuesIterator = property.getCollectionValue().iterator();
                while (valuesIterator.hasNext()) {
                    ClientValue value = valuesIterator.next();

                }
            }
        }
    }

    public Record convertToRecord(ClientEntity entity, Schema schema, RecordBuilderFactory builderFactory) {
        Record.Builder rb = builderFactory.newRecordBuilder(schema);
        for (Schema.Entry entry : schema.getEntries()) {
            ClientProperty property = entity.getProperty(entry.getName());
            if (property.hasCollectionValue()) {
                Iterator<ClientValue> valuesIterator = property.getCollectionValue().iterator();
                while (valuesIterator.hasNext()) {
                    ClientValue value = valuesIterator.next();

                }
            }
        }
        return rb.build();
    }

    public void setValue(ClientProperty property, Schema schema, Record.Builder recordBuilder,
            RecordBuilderFactory builderFactory) {

    }

    public void setValue(ClientValue value, Schema.Entry entry, Record.Builder recordBuilder,
            RecordBuilderFactory builderFactory) {
        switch (entry.getType()) {
        case INT:
            recordBuilder.withInt(entry, (Integer) value.asPrimitive().toValue());
            break;
        case LONG:
            recordBuilder.withLong(entry, (Long) value.asPrimitive().toValue());
            break;
        case BOOLEAN:
            recordBuilder.withBoolean(entry, (Boolean) value.asPrimitive().toValue());
            break;
        case FLOAT:
            float floatValue;
            if (value.getTypeName().equals("Edm.Decimal")) {
                floatValue = ((BigDecimal) value.asPrimitive().toValue()).floatValue();
            } else {
                floatValue = ((Float) value.asPrimitive().toValue()).floatValue();
            }
            recordBuilder.withFloat(entry, floatValue);
            break;
        case DOUBLE:
            recordBuilder.withDouble(entry, (Double) value.asPrimitive().toValue());
            break;
        case BYTES:
            byte[] bytesValue;
            if ("Edm.Binary".equals(value.getTypeName())) {
                bytesValue = (byte[]) value.asPrimitive().toValue();
            } else {
                EdmPrimitiveType binaryType = EdmPrimitiveTypeFactory
                        .getInstance(org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind.Binary);
                try {
                    bytesValue = (byte[]) binaryType.valueOfString(value.toString(), null, null,
                            org.apache.olingo.commons.api.Constants.DEFAULT_PRECISION,
                            org.apache.olingo.commons.api.Constants.DEFAULT_SCALE, null, byte[].class);
                } catch (EdmPrimitiveTypeException e) {
                    bytesValue = null;
                }
            }
            recordBuilder.withBytes(entry, bytesValue);
            break;
        case DATETIME:
            recordBuilder.withDateTime(entry, (Timestamp) value.asPrimitive().toValue());
            break;
        case RECORD:
            Record.Builder innerRecordBuilder = builderFactory.newRecordBuilder(entry.getElementSchema());
            entry.getElementSchema().getEntries().stream()
                    .forEach(innerEntry -> setValue(value.asComplex().get(innerEntry.getName()).getValue(), innerEntry,
                            innerRecordBuilder, builderFactory));
            recordBuilder.withRecord(entry, innerRecordBuilder.build());
            break;
        case STRING:
        default:
            recordBuilder.withString(entry, value.asPrimitive().toValue().toString());
            break;
        }
    }

}