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
package org.talend.components.dynamicscrm.datastore;

import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Checkable;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.type.DataStore;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.widget.Credential;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;

import lombok.Data;

import static org.talend.components.dynamicscrm.service.UIActionService.ACTION_HEALTHCHECK;
import static org.talend.sdk.component.api.configuration.ui.layout.GridLayout.FormType.ADVANCED;

@Data
@Checkable(ACTION_HEALTHCHECK)
@DataStore("DynamicsCrmConnection")
@GridLayout({ @GridLayout.Row({ "appType" }), @GridLayout.Row({ "username", "password" }), @GridLayout.Row({ "serviceRootUrl" }),
        @GridLayout.Row({ "clientId" }), @GridLayout.Row({ "clientSecret" }), @GridLayout.Row({ "authorizationEndpoint" }) })
@GridLayout(names = ADVANCED, value = { @GridLayout.Row("timeout"), @GridLayout.Row("maxRetries") })
@Documentation("Dynamics 365 CRM Connection class")
public class DynamicsCrmConnection implements Serializable {

    @Option
    @Required
    @Documentation("Select the type of your application, either Native App or Web App with delegated permissions.")
    private AppType appType = AppType.Native;

    @Option
    @Required
    @Documentation("User name")
    private String username;

    @Option
    @Required
    @Credential
    @Documentation("Password")
    private String password;

    @Option
    @Required
    @Documentation("Service Root URL")
    private String serviceRootUrl;

    @Option
    @Required
    @Documentation("Client ID")
    private String clientId;

    @Option
    @Credential
    @ActiveIf(target = "appType", value = "Web")
    @Documentation("Client secret")
    private String clientSecret;

    @Option
    @Required
    @Documentation("OAuth authorization endpoint")
    private String authorizationEndpoint;

    @Option
    @Required
    @Documentation("Timeout")
    private Integer timeout = 60;

    @Option
    @Required
    @Documentation("Max retries")
    private Integer maxRetries = 5;

    public enum AppType {
        Native,
        Web
    }

}