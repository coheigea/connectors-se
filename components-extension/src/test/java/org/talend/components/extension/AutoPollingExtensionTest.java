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
package org.talend.components.extension;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.talend.components.extension.components.ValidConnector;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.manager.chain.Job;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.talend.sdk.component.junit.SimpleFactory.configurationByExample;

@Slf4j
@WithComponents(value = "org.talend.components.extension.components")
public class AutoPollingExtensionTest {

    @Injected
    private BaseComponentsHandler componentsHandler;

    @Test
    public void test() {
        componentsHandler.injectServices(this);

        ValidConnector.TestConfig config = new ValidConnector.TestConfig();

        final String configStr = configurationByExample().forInstance(config).configured().toQueryString();

        Job.components() //
                .component("emitter", "Test://InputPolling?" + configStr) //
                .component("out", "test://collector") //
                .connections() //
                .from("emitter") //
                .to("out") //
                .build() //
                .run();

        final List<Record> records = componentsHandler.getCollectedData(Record.class);

        log.info("Retrieve "+records.size()+" records.");
        assertEquals(1, records.size());

        log.info("FIN -------------------------------------------------------------------------------");

    }

}