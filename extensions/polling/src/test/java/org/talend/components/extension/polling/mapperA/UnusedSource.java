/*
 * Copyright (C) 2006-2021 Talend Inc. - www.talend.com
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
package org.talend.components.extension.polling.mapperA;

import lombok.AllArgsConstructor;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.Producer;

import java.io.Serializable;

@Emitter(family = "mytest", name = "UnusedSource")
public class UnusedSource implements Serializable {

    private final UnusedConfig config;

    private boolean done = false;

    public UnusedSource(@Option("configuration") final UnusedConfig config) {
        this.config = config;
    }

    @Producer
    public Data next() {
        return null;
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {

        private final String valueStr;

        private final int valueInt;

    }

}