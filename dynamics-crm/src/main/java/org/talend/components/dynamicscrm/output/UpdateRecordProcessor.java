/*
 * Copyright (C) 2006-2020 Talend Inc. - www.talend.com
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

import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.talend.components.dynamicscrm.service.DynamicsCrmException;
import org.talend.components.dynamicscrm.service.I18n;
import org.talend.ms.crm.odata.DynamicsCRMClient;
import org.talend.sdk.component.api.record.Record;

import javax.naming.ServiceUnavailableException;
import java.util.List;

public class UpdateRecordProcessor extends AbstractToEntityRecordProcessor {

    public UpdateRecordProcessor(DynamicsCRMClient client, I18n i18n, EdmEntitySet entitySet,
            DynamicsCrmOutputConfiguration configuration, Edm metadata, List<String> columnNames) {
        super(client, i18n, entitySet, configuration, metadata, columnNames);
    }

    @Override
    protected void doProcessRecord(ClientEntity entity, Record record) throws ServiceUnavailableException {
        // There is only one key in Microsoft CRM objects
        client.updateEntity(entity,
                record.getString(entitySet.getEntityType().getKeyPropertyRefs().get(0).getProperty().getName()));
    }
}
