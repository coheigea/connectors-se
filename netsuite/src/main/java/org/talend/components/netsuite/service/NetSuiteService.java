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
import org.talend.components.netsuite.runtime.NetSuiteDatasetRuntime;
import org.talend.components.netsuite.runtime.NetSuiteEndpoint;
import org.talend.components.netsuite.runtime.client.NetSuiteClientService;
import org.talend.components.netsuite.runtime.v2018_2.client.NetSuiteClientFactoryImpl;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.completion.SuggestionValues;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class NetSuiteService {

    private NetSuiteDatasetRuntime dataSetRuntime;

    private NetSuiteClientService<?> clientService;

    private RecordBuilderFactory recordBuilderFactory;

    private Messages i18n;

    public NetSuiteService(RecordBuilderFactory recordBuilderFactory, Messages i18n) {
        this.recordBuilderFactory = recordBuilderFactory;
        this.i18n = i18n;
    }

    public synchronized void connect(NetSuiteDataStore dataStore) {
        NetSuiteEndpoint endpoint = new NetSuiteEndpoint(NetSuiteClientFactoryImpl.getFactory(), i18n, dataStore);
        clientService = endpoint.getClientService();
        dataSetRuntime = new NetSuiteDatasetRuntime(clientService.getMetaDataSource(), recordBuilderFactory);
    }

    List<SuggestionValues.Item> getRecordTypes(NetSuiteDataSet dataSet) {
        init(dataSet);
        return dataSetRuntime.getRecordTypes().stream()
                .map(record -> new SuggestionValues.Item(record.getName(), record.getDisplayName()))
                .sorted(Comparator.comparing(i -> i.getLabel().toLowerCase())).collect(Collectors.toList());
    }

    List<SuggestionValues.Item> getSearchTypes(NetSuiteDataSet dataSet) {
        if (StringUtils.isEmpty(dataSet.getRecordType())) {
            return Collections.emptyList();
        }
        init(dataSet);
        return dataSetRuntime.getSearchInfo(dataSet.getRecordType()).getFields().stream()
                .map(searchType -> new SuggestionValues.Item(searchType, searchType)).collect(Collectors.toList());
    }

    List<SuggestionValues.Item> getSearchFieldOperators(NetSuiteDataSet dataSet, String field) {
        init(dataSet);
        return dataSetRuntime.getSearchFieldOperators(dataSet.getRecordType(), field).stream()
                .map(searchField -> new SuggestionValues.Item(searchField, searchField)).collect(Collectors.toList());
    }

    public Schema getSchema(NetSuiteDataSet dataSet, List<String> stringSchema) {
        init(dataSet);
        return dataSetRuntime.getSchema(dataSet.getRecordType(), stringSchema);
    }

    public NetSuiteClientService<?> getClientService(NetSuiteDataSet dataSet) {
        init(dataSet);
        return clientService;
    }

    private void init(NetSuiteDataSet dataSet) {
        if (clientService == null) {
            connect(dataSet.getDataStore());
        }
        clientService.getMetaDataSource().setCustomizationEnabled(dataSet.isEnableCustomization());
    }

}