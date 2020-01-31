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
package org.talend.components.dynamicscrm.output;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.talend.sdk.component.junit.JoinInputFactory;
import org.talend.sdk.component.junit.SimpleComponentRule;
import org.talend.sdk.component.runtime.output.Processor;

public class DynamicsCrmOutputTest {

    @ClassRule
    public static final SimpleComponentRule COMPONENT_FACTORY =
            new SimpleComponentRule("org.talend.components.dynamicscrm");

    @Test
    @Ignore("You need to complete this test")
    public void map() throws IOException {

        // Output configuration
        // Setup your component configuration for the test here
        final DynamicsCrmOutputConfiguration configuration = new DynamicsCrmOutputConfiguration()
        /* .setDataset() */;

        // We create the component processor instance using the configuration filled above
        final Processor processor = COMPONENT_FACTORY.createProcessor(DynamicsCrmOutput.class, configuration);

        // The join input factory construct inputs test data for every input branch you have defined for this component
        // Make sure to fil in some test data for the branches you want to test
        // You can also remove the branches that you don't need from the factory below
        final JoinInputFactory joinInputFactory = new JoinInputFactory()
                .withInput("__default__",
                        asList(/* TODO - list of your input data for this branch. Instances of Record.class */));

        // Run the flow and get the outputs
        final SimpleComponentRule.Outputs outputs = COMPONENT_FACTORY.collect(processor, joinInputFactory);

        // TODO - Test Asserts
        assertEquals(0, outputs.size()); // test of the output branches count of the component

    }

}