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
package org.talend.components.rest.configuration;

import lombok.Data;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.constraint.Min;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.configuration.ui.DefaultValue;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Version(1)
@Data
@DataSet("Dataset")
@GridLayout({ @GridLayout.Row({ "datastore" }), @GridLayout.Row({ "resource" }), @GridLayout.Row({ "methodType" }),
        @GridLayout.Row({ "hasHeaders" }), @GridLayout.Row({ "headers" }), @GridLayout.Row({ "hasQueryParams" }),
        @GridLayout.Row({ "queryParams" }), @GridLayout.Row({ "hasPathParams" }), @GridLayout.Row({ "pathParams" }),
        @GridLayout.Row({ "hasBody" }), @GridLayout.Row({ "body" }) })
@GridLayout(names = GridLayout.FormType.ADVANCED, value = { @GridLayout.Row({ "datastore" }), @GridLayout.Row({ "maxRedirect" }),
        @GridLayout.Row({ "only_same_host" }), @GridLayout.Row({ "force_302_redirect" }) })
@Documentation("Dataset configuration.")
public class Dataset implements Serializable {

    @Option
    @Documentation("Identification of the REST API.")
    private Datastore datastore;

    @Option
    @Required
    @DefaultValue("GET")
    @Documentation("The HTTP verb to use.")
    private HttpMethod methodType;

    @Option
    @Required
    @Documentation("End of url to complete base url of the datastore.")
    private String resource = "";

    @Option
    @Documentation("How many redirection are supported ? (-1 for infinite)")
    @DefaultValue("3")
    @Min(-1)
    private Integer maxRedirect = 3;

    @Option
    @Documentation("Redirect only if same host.")
    @DefaultValue("false")
    @ActiveIf(target = "maxRedirect", value = "0", negate = true)
    private Boolean only_same_host = false;

    @Option
    @Documentation("Force a GET on a 302 redirection.")
    @DefaultValue("false")
    @ActiveIf(target = "maxRedirect", value = "0", negate = true)
    private Boolean force_302_redirect = false;

    @Option
    @Documentation("Does the request have parameters in URL ?")
    private Boolean hasPathParams = false;

    @Option
    @ActiveIf(target = "hasPathParams", value = "true")
    @Documentation("Path parameters.")
    private List<Param> pathParams = new ArrayList<>(Collections.singleton(new Param("", "")));

    @Option
    @Documentation("Does the request have headers ?")
    private Boolean hasHeaders = false;

    @Option
    @ActiveIf(target = "hasHeaders", value = "true")
    @Documentation("Query headers.")
    private List<Param> headers = new ArrayList<>(Collections.singleton(new Param("", "")));

    @Option
    @Documentation("Does the request have query paramters ?")
    private Boolean hasQueryParams = false;

    @Option
    @ActiveIf(target = "hasQueryParams", value = "true")
    @Documentation("Query parameters.")
    private List<Param> queryParams = new ArrayList<>(Collections.singleton(new Param("", "")));

    @Option
    @Documentation("Does the request have a body ?")
    private boolean hasBody;

    @Option
    @ActiveIf(target = "hasBody", value = "true")
    @Documentation("Request body")
    private RequestBody body;

    public boolean supportRedirect() {
        return this.getMaxRedirect() != 0;
    }

}
