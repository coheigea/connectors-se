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
package org.talend.components.netsuite.source;

import lombok.Setter;
import org.talend.components.netsuite.dataset.NetSuiteInputProperties;
import org.talend.components.netsuite.runtime.client.NetSuiteClientService;
import org.talend.components.netsuite.runtime.client.NsSearchResult;
import org.talend.components.netsuite.runtime.client.search.PageSelection;
import org.talend.components.netsuite.runtime.client.search.SearchResultSet;
import org.talend.components.netsuite.service.Messages;
import org.talend.components.netsuite.service.NetSuiteService;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Assessor;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.PartitionMapper;
import org.talend.sdk.component.api.input.PartitionSize;
import org.talend.sdk.component.api.input.Split;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Version(1)
@Icon(value = Icon.IconType.NETSUITE)
@PartitionMapper(name = "Input")
@Documentation("Creates worker emitter with specified configurations")
public class NetSuiteInputMapper implements Serializable {

    private final NetSuiteInputProperties configuration;

    private final NetSuiteService service;

    private final RecordBuilderFactory recordBuilderFactory;

    private final Messages i18n;

    @Setter
    private NsSearchResult<?> rs;

    @Setter
    private PageSelection pageSelection;

    public NetSuiteInputMapper(@Option("configuration") final NetSuiteInputProperties configuration,
            final NetSuiteService service, final RecordBuilderFactory recordBuilderFactory, final Messages i18n) {
        this.configuration = configuration;
        this.service = service;
        this.recordBuilderFactory = recordBuilderFactory;
        this.i18n = i18n;
    }

    @PostConstruct
    public void init() {
        NetSuiteClientService<?> clientService = service.getClientService(configuration.getDataSet().getDataStore());
        NetSuiteInputSearcher searcher = new NetSuiteInputSearcher(configuration, clientService);
        rs = searcher.search();
    }

    @Assessor
    public long estimateSize() {
        return rs.getTotalPages();
    }

    @Split
    public List<NetSuiteInputMapper> split(@PartitionSize final long bundles) {
        if (bundles == 0)
            return Collections.singletonList(this);

        int threads = (int) (rs.getTotalPages() / bundles);
        List<NetSuiteInputMapper> res = new LinkedList<>();
        for (int i = 0; i < threads; i++) {
            NetSuiteInputMapper mapper = new NetSuiteInputMapper(configuration, service, recordBuilderFactory, i18n);
            mapper.setRs(rs);
            mapper.setPageSelection(new PageSelection((int) (bundles * i) + 1, (int) bundles));
            res.add(mapper);
        }
        if (threads * bundles < rs.getTotalPages()) {
            NetSuiteInputMapper mapper = new NetSuiteInputMapper(configuration, service, recordBuilderFactory, i18n);
            mapper.setRs(rs);
            mapper.setPageSelection(new PageSelection((int) bundles * threads + 1, rs.getTotalPages() - (int) bundles * threads));
            res.add(mapper);
        }
        return res;
    }

    @Emitter
    public NetSuiteInputSource createWorker() {
        NetSuiteClientService<?> clientService = service.getClientService(configuration.getDataSet().getDataStore());
        String recordTypeName = configuration.getDataSet().getRecordType();
        SearchResultSet<?> srs = new SearchResultSet<>(clientService, recordTypeName, rs, pageSelection);
        return new NetSuiteInputSource(configuration, service, recordBuilderFactory, i18n, srs);
    }
}