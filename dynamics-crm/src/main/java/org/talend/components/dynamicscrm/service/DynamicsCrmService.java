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
package org.talend.components.dynamicscrm.service;

import org.talend.components.dynamicscrm.datastore.DynamicsCrmConnection;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.healthcheck.HealthCheck;
import org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus;

import static org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus.Status.OK;

@Service
public class DynamicsCrmService {

    public static final String ACTION_HEALTHCHECK = "ACTION_HEALTHCHECK";

    // you can put logic here you can reuse in components
    @HealthCheck(ACTION_HEALTHCHECK)
    public HealthCheckStatus validateConnection(@Option final DynamicsCrmConnection connection) {
        return new HealthCheckStatus(OK, "OK");
    }

}