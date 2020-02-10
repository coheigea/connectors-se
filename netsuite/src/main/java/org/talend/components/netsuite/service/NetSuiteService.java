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
package org.talend.components.netsuite.service;

import org.apache.commons.lang3.StringUtils;
import org.talend.components.netsuite.dataset.NetSuiteDataSet;
import org.talend.components.netsuite.datastore.NetSuiteDataStore;
import org.talend.components.netsuite.runtime.NetSuiteEndpoint;
import org.talend.components.netsuite.runtime.client.MetaDataSource;
import org.talend.components.netsuite.runtime.client.NetSuiteClientFactory;
import org.talend.components.netsuite.runtime.client.NetSuiteClientService;
import org.talend.components.netsuite.runtime.model.CustomFieldDesc;
import org.talend.components.netsuite.runtime.model.FieldDesc;
import org.talend.components.netsuite.runtime.model.SearchRecordTypeDesc;
import org.talend.components.netsuite.runtime.model.TypeDesc;
import org.talend.components.netsuite.runtime.model.beans.Beans;
import org.talend.components.netsuite.runtime.model.customfield.CustomFieldRefType;
import org.talend.components.netsuite.runtime.model.search.SearchInfo;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.completion.SuggestionValues;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.xml.datatype.XMLGregorianCalendar;

import static java.util.stream.Collectors.toList;

@Service
public class NetSuiteService {

    public static final Predicate<String> FILTER_EXTRA_SEARCH_FIELDS = name -> !"recType".equals(name)
            && !"customFieldList".equals(name);

    // private NetSuiteClientService<?> clientService;

    private transient Map<NetSuiteDataSet, NetSuiteClientService> dataSetToClientService;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Service
    private Messages i18n;

    @PostConstruct
    public void init() {
        dataSetToClientService = new ConcurrentHashMap<>();
    }

    // public NetSuiteService(RecordBuilderFactory recordBuilderFactory, Messages i18n) {
    // this.recordBuilderFactory = recordBuilderFactory;
    // this.i18n = i18n;
    // }

    public synchronized NetSuiteClientService connect(NetSuiteDataStore dataStore) {
        NetSuiteClientFactory<?> netSuiteClientFactory;
        switch (dataStore.getApiVersion()) {
        case V2019_2:
            netSuiteClientFactory = org.talend.components.netsuite.runtime.v2019_2.client.NetSuiteClientFactoryImpl.getFactory();
            break;
        default:
            throw new RuntimeException("Unknown API version: " + dataStore.getApiVersion());
        }
        NetSuiteEndpoint endpoint = new NetSuiteEndpoint(netSuiteClientFactory, i18n, dataStore);
        return endpoint.getClientService();
    }

    List<SuggestionValues.Item> getRecordTypes(NetSuiteDataSet dataSet) {
        NetSuiteClientService clientService = init(dataSet);
        return clientService.getMetaDataSource().getRecordTypes().stream()
                .map(record -> new SuggestionValues.Item(record.getName(), record.getDisplayName()))
                .sorted(Comparator.comparing(i -> i.getLabel().toLowerCase())).collect(Collectors.toList());
    }

    List<SuggestionValues.Item> getSearchTypes(NetSuiteDataSet dataSet) {
        if (StringUtils.isEmpty(dataSet.getRecordType())) {
            return Collections.emptyList();
        }
        NetSuiteClientService clientService = init(dataSet);
        MetaDataSource metaDataSource = clientService.getMetaDataSource();
        final SearchRecordTypeDesc searchInfo = metaDataSource.getSearchRecordType(dataSet.getRecordType());
        final TypeDesc searchRecordInfo = metaDataSource.getBasicMetaData().getTypeInfo(searchInfo.getSearchBasicClass());
        List<String> fields = searchRecordInfo.getFields().stream().map(FieldDesc::getName).filter(FILTER_EXTRA_SEARCH_FIELDS)
                .sorted().collect(toList());
        fields.addAll(metaDataSource.getSearchRecordCustomFields(dataSet.getRecordType()));
        return new SearchInfo(searchRecordInfo.getTypeName(), fields).getFields().stream()
                .map(searchType -> new SuggestionValues.Item(searchType, searchType)).collect(Collectors.toList());
    }

    List<SuggestionValues.Item> getSearchFieldOperators(NetSuiteDataSet dataSet, String field) {
        NetSuiteClientService clientService = init(dataSet);
        MetaDataSource metaDataSource = clientService.getMetaDataSource();
        final SearchRecordTypeDesc searchInfo = metaDataSource.getSearchRecordType(dataSet.getRecordType());
        final FieldDesc fieldDesc = metaDataSource.getBasicMetaData().getTypeInfo(searchInfo.getSearchBasicClass())
                .getField(field);
        return metaDataSource.getBasicMetaData().getSearchOperatorNames(fieldDesc).stream()
                .map(searchField -> new SuggestionValues.Item(searchField, searchField)).collect(Collectors.toList());
    }

    public Schema getSchema(NetSuiteDataSet dataSet, List<String> stringSchema) {
        NetSuiteClientService clientService = init(dataSet);
        final boolean schemaNotDesigned = (stringSchema == null);
        Schema.Builder builder = recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD);
        clientService.getMetaDataSource().getTypeInfo(dataSet.getRecordType()).getFields().stream()
                .filter(field -> schemaNotDesigned
                        || stringSchema.stream().anyMatch(element -> element.equalsIgnoreCase(field.getName())))
                .sorted(new FieldDescComparator()).map(this::buildEntryFromFieldDescription).forEach(builder::withEntry);
        return builder.build();
    }

    private Schema.Entry buildEntryFromFieldDescription(FieldDesc desc) {
        return recordBuilderFactory.newEntryBuilder().withName(Beans.toInitialUpper(desc.getName()))
                .withType(inferSchemaForField(desc)).withNullable(desc.isNullable()).build();
    }

    private Schema.Type inferSchemaForField(FieldDesc fieldDesc) {
        if (fieldDesc instanceof CustomFieldDesc) {
            CustomFieldDesc customFieldInfo = (CustomFieldDesc) fieldDesc;
            CustomFieldRefType customFieldRefType = customFieldInfo.getCustomFieldType();
            switch (customFieldRefType) {
            case BOOLEAN:
                return Schema.Type.BOOLEAN;
            case LONG:
                return Schema.Type.LONG;
            case DOUBLE:
                return Schema.Type.DOUBLE;
            case DATE:
                return Schema.Type.DATETIME;
            default:
                return Schema.Type.STRING;
            }
        } else {
            Class<?> fieldType = fieldDesc.getValueType();
            if (fieldType == Boolean.TYPE || fieldType == Boolean.class) {
                return Schema.Type.BOOLEAN;
            } else if (fieldType == Integer.TYPE || fieldType == Integer.class) {
                return Schema.Type.INT;
            } else if (fieldType == Long.TYPE || fieldType == Long.class) {
                return Schema.Type.LONG;
            } else if (fieldType == Float.TYPE || fieldType == Float.class) {
                return Schema.Type.FLOAT;
            } else if (fieldType == Double.TYPE || fieldType == Double.class) {
                return Schema.Type.DOUBLE;
            } else if (fieldType == XMLGregorianCalendar.class) {
                return Schema.Type.DATETIME;
            } else {
                return Schema.Type.STRING;
            }
        }
    }

    public NetSuiteClientService<?> getClientService(NetSuiteDataSet dataSet) {
        return init(dataSet);
    }

    private NetSuiteClientService init(NetSuiteDataSet dataSet) {
        NetSuiteClientService clientService = dataSetToClientService.computeIfAbsent(dataSet, ds -> connect(ds.getDataStore()));
        clientService.getMetaDataSource().setCustomizationEnabled(dataSet.isEnableCustomization());
        return clientService;
    }

    private static class FieldDescComparator implements Comparator<FieldDesc> {

        @Override
        public int compare(FieldDesc o1, FieldDesc o2) {
            int result = Boolean.compare(o1.isKey(), o2.isKey());
            if (result != 0) {
                return result * -1;
            }
            result = o1.getName().compareTo(o2.getName());
            return result;
        }
    }

    /**
     * Return type of value hold by a custom field with given <code>CustomFieldRefType</code>.
     *
     * @param customFieldRefType type of field
     * @return type of value
     */
    public static Class<?> getCustomFieldValueClass(CustomFieldRefType customFieldRefType) {
        switch (customFieldRefType) {
        case BOOLEAN:
            return Boolean.class;
        case STRING:
            return String.class;
        case LONG:
            return Long.class;
        case DOUBLE:
            return Double.class;
        case DATE:
            return XMLGregorianCalendar.class;
        case SELECT:
        case MULTI_SELECT:
            return String.class;
        default:
            return null;
        }
    }
}