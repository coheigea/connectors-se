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
package org.talend.components.extension.internal.builtin.test.component.virtual;

import java.io.Serializable;
import java.util.List;

import org.talend.components.extension.api.virtual.VirtualChain;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.Producer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Emitter(name = "Source")
@VirtualChain(name = "SourceWithFlatMapJson", icon = "foo", followedBy = FlatMapJson.class)
public class StaticSource implements Serializable {

    private final Configuration configuration;

    @Producer
    public Person next() {
        return configuration.values == null || configuration.values.isEmpty() ? null : configuration.values.remove(0);
    }

    @Data
    public static class Configuration {

        @Option
        private List<Person> values;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Name {

        @Option
        private String shortName;

        @Option
        private String longName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Person {

        @Option
        private List<Name> names;

        @Option
        private int age;
    }
}
