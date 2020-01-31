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
import org.apache.olingo.client.api.domain.*;
import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.commons.api.Constants;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
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

    public QueryOptionConfig createQueryOptionConfig(String[] fields, DynamicsCrmInputMapperConfiguration configuration) {
        QueryOptionConfig config = new QueryOptionConfig();
        config.setReturnEntityProperties(fields);
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

    public Schema getSchemaForEntitySet(DynamicsCRMClient client, String entitySetName, List<String> columnNames,
            RecordBuilderFactory builderFactory) {
        Edm metadata = getMetadata(client);
        return parseSchema(metadata, entitySetName, columnNames, builderFactory);
    }

    public Schema getSchemaFromMetadata(Edm metadata, String entitySetName, List<String> columnNames,
            RecordBuilderFactory builderFactory) {
        return parseSchema(metadata, entitySetName, columnNames, builderFactory);
    }

    public Edm getMetadata(DynamicsCRMClient client) {
        EdmMetadataRequest metadataRequest = client.createMetadataRetrieveRequest();
        Edm metadata;
        try {
            ODataRetrieveResponse<Edm> metadataResponse = metadataRequest.execute();
            metadata = metadataResponse.getBody();
        } catch (Exception e) {
            throw new DynamicsCrmException(i18n.metadataRetrieveFailed(e.getMessage()));
        }
        return metadata;
    }

    private Schema parseSchema(Edm edm, String entitySetName, List<String> columnNames, RecordBuilderFactory builderFactory) {
        EdmEntityContainer container = edm.getEntityContainer();
        EdmEntitySet entitySet = container.getEntitySet(entitySetName);
        EdmEntityType type = entitySet.getEntityType();
        if (columnNames == null || columnNames.isEmpty()) {
            columnNames = type.getPropertyNames();
        }
        Schema.Builder schemaBuilder = builderFactory.newSchemaBuilder(Schema.Type.RECORD);
        columnNames.forEach(f -> schemaBuilder.withEntry(
                builderFactory.newEntryBuilder().withName(f).withType(getTckType((EdmProperty) type.getProperty(f), edm))
                        .withElementSchema(getSubSchema(edm, (EdmProperty) type.getProperty(f), builderFactory))
                        .withNullable(((EdmProperty) type.getProperty(f)).isNullable()).build()));
        return schemaBuilder.build();
    }

    private Schema parseSchema(Edm edm, EdmStructuredType type, RecordBuilderFactory builderFactory) {
        Schema.Builder schemaBuilder = builderFactory.newSchemaBuilder(Schema.Type.RECORD);
        type.getPropertyNames().stream()
                .forEach(f -> schemaBuilder.withEntry(
                        builderFactory.newEntryBuilder().withName(f).withType(getTckType((EdmProperty) type.getProperty(f), edm))
                                .withElementSchema(getSubSchema(edm, (EdmProperty) type.getProperty(f), builderFactory))
                                .withNullable(((EdmProperty) type.getProperty(f)).isNullable()).build()));
        return schemaBuilder.build();
    }

    private Schema getSubSchema(Edm edm, EdmProperty edmElement, RecordBuilderFactory builderFactory) {
        if (edmElement.getType().getKind() != EdmTypeKind.COMPLEX) {
            return builderFactory.newSchemaBuilder(getElementType(edmElement.getType())).build();
        }

        return parseSchema(edm, edm.getComplexType(edmElement.getType().getFullQualifiedName()), builderFactory);
    }

    private Schema.Type getTckType(EdmProperty edmElement, Edm edm) {
        if (edmElement.isCollection()) {
            return Schema.Type.ARRAY;
        }
        return getElementType(edmElement.getType());
    }

    private Schema.Type getElementType(EdmType edmType) {
        if (edmType.getKind() == EdmTypeKind.COMPLEX) {
            return Schema.Type.RECORD;
        }
        switch (edmType.getFullQualifiedName().getFullQualifiedNameAsString()) {
        case "Edm.Boolean":
            return Schema.Type.BOOLEAN;
        case "Edm.Binary":
            return Schema.Type.BYTES;
        case "Edm.Byte":
        case "Edm.SByte":
        case "Edm.Int16":
        case "Edm.Int32":
            return Schema.Type.INT;
        case "Edm.Int64":
            return Schema.Type.LONG;
        case "Edm.DateTime":
        case "Edm.DateTimeOffset":
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

    public Record createRecord(ClientEntity entity, Schema schema, RecordBuilderFactory builderFactory) {
        final Record.Builder recordBuilder = builderFactory.newRecordBuilder(schema);
        schema.getEntries().stream()
                .forEach(entry -> setValue(entity.getProperty(entry.getName()).getValue(), entry, recordBuilder, builderFactory));
        return recordBuilder.build();
    }

    private Record createRecord(ClientComplexValue value, Schema schema, RecordBuilderFactory builderFactory) {
        final Record.Builder recordBuilder = builderFactory.newRecordBuilder(schema);
        schema.getEntries().stream()
                .forEach(entry -> setValue(value.get(entry.getName()).getValue(), entry, recordBuilder, builderFactory));
        return recordBuilder.build();
    }

    private void setValue(ClientValue value, Schema.Entry entry, Record.Builder recordBuilder,
            RecordBuilderFactory builderFactory) {
        if (value == null) {
            return;
        }
        Object convertedValue = getValue(value, entry, builderFactory);
        if (convertedValue == null) {
            return;
        }
        final Schema.Entry.Builder entryBuilder = builderFactory.newEntryBuilder();
        Schema.Type type = entry.getType();
        entryBuilder.withNullable(entry.isNullable()).withName(entry.getName()).withType(type);

        try {
            switch (type) {
            case ARRAY:
                Schema elementSchema = entry.getElementSchema();
                entryBuilder.withElementSchema(elementSchema);
                Collection<Object> objects = (Collection<Object>) convertedValue;
                recordBuilder.withArray(entryBuilder.build(), objects);
                break;
            case INT:
                recordBuilder.withInt(entryBuilder.build(), (Integer) convertedValue);
                break;
            case LONG:
                recordBuilder.withLong(entryBuilder.build(), (Long) convertedValue);
                break;
            case BOOLEAN:
                recordBuilder.withBoolean(entryBuilder.build(), (Boolean) convertedValue);
                break;
            case FLOAT:
                recordBuilder.withFloat(entryBuilder.build(), (Float) convertedValue);
                break;
            case DOUBLE:
                recordBuilder.withDouble(entryBuilder.build(), (Double) convertedValue);
                break;
            case BYTES:
                recordBuilder.withBytes(entryBuilder.build(), (byte[]) convertedValue);
                break;
            case DATETIME:
                recordBuilder.withDateTime(entryBuilder.build(), (Timestamp) convertedValue);
                break;
            case RECORD:
                entryBuilder.withElementSchema(entry.getElementSchema());
                recordBuilder.withRecord(entryBuilder.build(), (Record) convertedValue);
                break;
            case STRING:
            default:
                recordBuilder.withString(entryBuilder.build(), (String) convertedValue);
                break;
            }
        } catch (Exception e) {
            System.out.println(value.getTypeName() + ": " + entry.getType() + "(" + entry.getElementSchema() + ") = "
                    + value.asPrimitive().toValue());
        }
    }

    private Object getValue(ClientValue value, Schema.Entry entry, RecordBuilderFactory builderFactory) {
        return getValue(value, entry.getType(), entry.getElementSchema(), builderFactory);
    }

    private Object getValue(ClientValue value, Schema schema, RecordBuilderFactory builderFactory) {
        return getValue(value, schema.getType(), schema, builderFactory);
    }

    private Object getValue(ClientValue value, Schema.Type type, Schema elementSchema, RecordBuilderFactory builderFactory) {
        if (value == null || (value.isPrimitive() && value.asPrimitive().toValue() == null)) {
            return null;
        }

        if (value.isEnum()) {
            return value.asEnum().getValue();
        }
        switch (type) {
        case ARRAY:
            final Collection<Object> objects = new ArrayList<>();
            value.asCollection().forEach(val -> objects.add(getValue(val, elementSchema, builderFactory)));
            return objects;
        case INT:
        case LONG:
        case DOUBLE:
        case DATETIME:
        case BOOLEAN:
            return value.asPrimitive().toValue();
        case FLOAT:
            float floatValue;
            if (value.getTypeName().equals("Edm.Decimal")) {
                floatValue = ((BigDecimal) value.asPrimitive().toValue()).floatValue();
            } else {
                floatValue = ((Float) value.asPrimitive().toValue()).floatValue();
            }
            return floatValue;
        case BYTES:
            byte[] bytesValue;
            if ("Edm.Binary".equals(value.getTypeName())) {
                bytesValue = (byte[]) value.asPrimitive().toValue();
            } else {
                EdmPrimitiveType binaryType = EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Binary);
                try {
                    bytesValue = binaryType.valueOfString(value.toString(), null, null, Constants.DEFAULT_PRECISION,
                            Constants.DEFAULT_SCALE, null, byte[].class);
                } catch (EdmPrimitiveTypeException e) {
                    String errorMessage = i18n.failedParsingBytesValue(e.getMessage());
                    log.error(errorMessage);
                    throw new DynamicsCrmException(errorMessage, e);
                }
            }
            return bytesValue;
        case RECORD:
            if (value.asComplex() == null) {
                return null;
            }
            return createRecord(value.asComplex(), elementSchema, builderFactory);
        case STRING:
        default:
            return value.toString();
        }
    }

    protected URIBuilder createUriBuilderForValidProps(DynamicsCRMClient client, DynamicsCrmConnection datastore, String entitySetName) {
        return client.getClient().newURIBuilder(datastore.getServiceRootUrl()).appendEntitySetSegment("EntityDefinitions")
                                   .appendKeySegment(Collections.singletonMap("LogicalName", entitySetName))
                                   .appendEntitySetSegment("Attributes").select("LogicalName", "IsValidForRead", "IsValidForUpdate", "IsValidForCreate");
    }

    public List<PropertyValidationData> getPropertiesValidationData(DynamicsCRMClient client, DynamicsCrmConnection datastore, String entitySetName) {
        ODataEntitySetRequest<ClientEntitySet> validationDataRequest = client.createRequest(createUriBuilderForValidProps(client, datastore, entitySetName));
        ODataRetrieveResponse<ClientEntitySet> validationDataResponse = validationDataRequest.execute();
        ClientEntitySet validationDataSet = validationDataResponse.getBody();
        List<PropertyValidationData> validationData = validationDataSet.getEntities().stream()
                .map(e -> new PropertyValidationData((String)e.getProperty("LogicalName").getPrimitiveValue().toValue(), (boolean)e.getProperty("IsValidForCreate").getPrimitiveValue().toValue(), (boolean)e.getProperty("IsValidForUpdate").getPrimitiveValue().toValue(), (boolean)e.getProperty("IsValidForRead").getPrimitiveValue().toValue()))
                .collect(Collectors.toList());
        return validationData;
    }

}