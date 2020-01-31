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

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.naming.AuthenticationException;

import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.talend.components.dynamicscrm.service.DynamicsCrmException;
import org.talend.components.dynamicscrm.service.DynamicsCrmService;
import org.talend.components.dynamicscrm.service.I18n;
import org.talend.components.dynamicscrm.service.PropertyValidationData;
import org.talend.ms.crm.odata.DynamicsCRMClient;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.AfterGroup;
import org.talend.sdk.component.api.processor.BeforeGroup;
import org.talend.sdk.component.api.processor.ElementListener;
import org.talend.sdk.component.api.processor.Input;
import org.talend.sdk.component.api.processor.Processor;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;

@Version(1) // default version is 1, if some configuration changes happen between 2 versions you can add a
            // migrationHandler
@Icon(Icon.IconType.STAR) // you can use a custom one using @Icon(value=CUSTOM, custom="filename") and adding
                          // icons/filename.svg
                          // in resources
@Processor(name = "DynamicsCrmOutput")
@Documentation("TODO fill the documentation for this processor")
public class DynamicsCrmOutput implements Serializable {

    private final DynamicsCrmOutputConfiguration configuration;

    private final DynamicsCrmService service;

    private DynamicsCRMClient client;

    private Edm metadata;

    @Service
    private I18n i18n;

    private List<String> fields;

    public DynamicsCrmOutput(@Option("configuration") final DynamicsCrmOutputConfiguration configuration,
            final DynamicsCrmService service) {
        this.configuration = configuration;
        this.service = service;
    }

    @PostConstruct
    public void init() {
        try {
            client = service
                    .createClient(configuration.getDataset().getDatastore(), configuration.getDataset().getEntitySet());
        } catch (AuthenticationException e) {
            throw new DynamicsCrmException(i18n.authenticationFailed(e.getMessage()));
        }
        metadata = service.getMetadata(client);
        EdmEntitySet entitySet = metadata.getEntityContainer().getEntitySet(configuration.getDataset().getEntitySet());
        if (fields == null || fields.isEmpty()) {
            fields = service
                    .getPropertiesValidationData(client, configuration.getDataset().getDatastore(),
                            entitySet.getEntityType().getName())
                    .stream()
                    .filter(getFilter())
                    .map(PropertyValidationData::getName)
                    .collect(Collectors.toList());
        }
    }

    private Predicate<? super PropertyValidationData> getFilter() {
        switch (configuration.getAction()) {
        case INSERT:
            return PropertyValidationData::isValidForCreate;
        case UPDATE:
            return PropertyValidationData::isValidForUpdate;
        default:
            return t -> {
                return true;
            };
        }
    }

    @BeforeGroup
    public void beforeGroup() {
    }

    @ElementListener
    public void onNext(@Input final Record defaultInput) {
        EdmEntitySet entitySet = metadata.getEntityContainer().getEntitySet(configuration.getDataset().getEntitySet());
        final Optional<String> keyProp =
                entitySet.getEntityType().getKeyPropertyRefs().stream().map(r -> r.getProperty().getName()).findFirst();
        Set<String> fieldsSet = new HashSet<>(fields);

        ClientEntity entity = client.newEntity();
        Map<String, String> lookup = configuration
                .getLookupMapping()
                .stream()
                .collect(Collectors.toMap(l -> l.getInputColumn(), l -> l.getReferenceEntitySet()));
        List<Schema.Entry> entries = defaultInput.getSchema().getEntries();
        String keyValue = null;
        for (Schema.Entry entry : entries) {
            if (entry.getName().equals(keyProp.get())) {
                keyValue = defaultInput.get(String.class, entry.getName());
                continue;
            } else if (fieldsSet.contains(entry.getName())) {
                client
                        .addEntityProperty(entity, entry.getName(),
                                EdmPrimitiveTypeKind
                                        .valueOfFQN(entitySet
                                                .getEntityType()
                                                .getProperty(entry.getName())
                                                .getType()
                                                .getFullQualifiedName()),
                                defaultInput.get(Object.class, entry.getName()));
            } else if (fieldsSet.contains(client.extractNavigationLinkName(entry.getName()))) {
                client
                        .addEntityNavigationLink(entity, lookup.get(entry.getName()),
                                client.extractNavigationLinkName(entry.getName()),
                                defaultInput.getString(entry.getName()), configuration.isEmptyStringToNull(),
                                configuration.isIgnoreNull());
            }

        }
    }

    @AfterGroup
    public void afterGroup() {
        // symmetric method of the beforeGroup() executed after the chunk processing
        // Note: if you don't need it you can delete it
    }

    @PreDestroy
    public void release() {
        // this is the symmetric method of the init() one,
        // release potential connections you created or data you cached
        // Note: if you don't need it you can delete it
    }
}