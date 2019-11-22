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
package org.talend.components.extension.components;

import org.talend.components.extension.Pollable;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.configuration.type.DataStore;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.record.Record;

import java.io.Serializable;

@Version
@Icon(value = Icon.IconType.STAR)
@Emitter(name = "Input")
@Pollable
@Documentation("Valid input test connector")
public class ValidConnector implements Serializable {

    private final TestConfig config;

    public ValidConnector(TestConfig config) {
        this.config = config;
    }

    @Producer
    public Record next() {
        return null;
    }

    public final static class TestConfig {

        @Option
        private TestDataset dataset = new TestDataset();

        @Option
        private String pipelineOption = "conf pipeline option";

    }

    @DataSet
    public final static class TestDataset {

        @Option
        private TestDatastore datastore = new TestDatastore();

        @Option
        private String entity = "conf entity";

    }

    @DataStore
    public final static class TestDatastore {

        @Option
        private String url = "conf url";

    }

}
