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

import org.apache.commons.lang3.StringUtils;
import org.talend.components.netsuite.dataset.NetSuiteOutputProperties;
import org.talend.components.netsuite.dataset.NetSuiteOutputProperties.DataAction;
import org.talend.components.netsuite.runtime.NsObjectTransducer;
import org.talend.components.netsuite.runtime.client.NetSuiteClientService;
import org.talend.components.netsuite.runtime.client.NsWriteResponse;
import org.talend.components.netsuite.runtime.model.beans.Beans;
import org.talend.components.netsuite.service.Messages;
import org.talend.components.netsuite.service.NetSuiteService;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.AfterGroup;
import org.talend.sdk.component.api.processor.BeforeGroup;
import org.talend.sdk.component.api.processor.ElementListener;
import org.talend.sdk.component.api.processor.Input;
import org.talend.sdk.component.api.processor.Processor;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Version(1)
@Icon(value = Icon.IconType.NETSUITE)
@Processor(name = "Output")
@Documentation("Output component processor")
public class NetSuiteOutputProcessor implements Serializable {

    private final NetSuiteOutputProperties configuration;

    private final NetSuiteService service;

    private NetSuiteClientService<?> clientService;

    private Messages i18n;

    protected NsObjectOutputTransducer transducer;

    private Function<List<?>, List<NsWriteResponse<?>>> dataActionFunction;

    private List<Record> inputRecordList;

    private Schema schema;

    public NetSuiteOutputProcessor(@Option("configuration") final NetSuiteOutputProperties configuration,
            final NetSuiteService service, Messages i18n) {
        this.configuration = configuration;
        this.service = service;
        this.i18n = i18n;
    }

    @PostConstruct
    public void init() {
        clientService = service.getClientService(configuration.getDataSet().getDataStore());
        schema = service.getSchema(configuration.getDataSet(), null);
        referenceDataActionFunction();
        instantiateTransducer();
        inputRecordList = new ArrayList<>();
    }

    private void referenceDataActionFunction() {
        DataAction data = configuration.getAction();
        switch (data) {
        case ADD:
            dataActionFunction = clientService::addList;
            break;
        case UPDATE:
            dataActionFunction = clientService::updateList;
            break;
        case DELETE:
            dataActionFunction = clientService::deleteList;
            break;
        case UPSERT:
            dataActionFunction = configuration.isUseNativeUpsert() ? clientService::upsertList : this::customUpsert;
            break;
        }
    }

    private void instantiateTransducer() {
        transducer = new NsObjectOutputTransducer(clientService, i18n, configuration.getDataSet().getRecordType(), schema,
                configuration.getDataSet().getDataStore().getApiVersion().getVersion());
        transducer.setMetaDataSource(clientService.getMetaDataSource());
        transducer.setReference(configuration.getAction() == DataAction.DELETE);
    }

    @BeforeGroup
    public void beforeGroup() {
        inputRecordList.clear();
    }

    @ElementListener
    public void onNext(@Input final Record record) {
        inputRecordList.add(record);
    }

    /**
     * Process and write given list of <code>Record</code>s.
     *
     * @param recordList list of records to be processed
     */
    private void write(List<Record> recordList) {
        if (recordList.isEmpty()) {
            return;
        }
        List<Object> nsObjectList = recordList.stream().map(transducer::write).collect(toList());
        dataActionFunction.apply(nsObjectList).stream().forEach(this::processWriteResponse);
        cleanWrites();
    }

    private void cleanWrites() {
        inputRecordList.clear();
    }

    private void processWriteResponse(NsWriteResponse<?> response) {
        if (!response.getStatus().isSuccess()) {
            NetSuiteClientService.checkError(response.getStatus());
        }
    }

    @AfterGroup
    public void afterGroup() {
        write(inputRecordList);
    }

    @PreDestroy
    public void release() {
        if (!inputRecordList.isEmpty()) {
            write(inputRecordList);
        }
    }

    private <T> List<NsWriteResponse<?>> customUpsert(List<T> records) {
        List<T> addList = null;
        List<T> updateList = null;
        for (T nsObject : records) {
            String internalId = (String) Beans.getSimpleProperty(nsObject, NsObjectTransducer.INTERNAL_ID);
            String externalId = (String) Beans.getSimpleProperty(nsObject, NsObjectTransducer.EXTERNAL_ID);
            if (StringUtils.isNotEmpty(internalId) || StringUtils.isNotEmpty(externalId)) {
                if (updateList == null) {
                    updateList = new ArrayList<>();
                }
                updateList.add(nsObject);
            } else {
                if (addList == null) {
                    addList = new ArrayList<>();
                }
                addList.add(nsObject);
            }
        }
        Map<T, NsWriteResponse<?>> responseMap = new HashMap<>(records.size());
        if (addList != null) {
            processOperationResponse(clientService::addList, addList, () -> responseMap);
        }
        if (updateList != null) {
            processOperationResponse(clientService::updateList, updateList, () -> responseMap);
        }

        // Create combined list of write responses with an order as input records list
        return records.stream().map(responseMap::get).collect(toList());
    }

    private <T> void processOperationResponse(Function<List<T>, List<NsWriteResponse<?>>> operation, List<T> recordsToBeProcessed,
            Supplier<Map<T, NsWriteResponse<?>>> supplier) {
        List<NsWriteResponse<?>> responseList = operation.apply(recordsToBeProcessed);
        IntStream.range(0, recordsToBeProcessed.size()).boxed()
                .collect(toMap(recordsToBeProcessed::get, responseList::get, (k1, k2) -> k2, supplier));
    }

}