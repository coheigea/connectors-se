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
import org.talend.sdk.component.api.component.MigrationHandler;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.service.configuration.LocalConfiguration;
import org.talend.sdk.component.container.Container;
import org.talend.sdk.component.runtime.input.Mapper;
import org.talend.sdk.component.runtime.manager.ComponentFamilyMeta;
import org.talend.sdk.component.runtime.manager.ComponentManager;
import org.talend.sdk.component.runtime.manager.ContainerComponentRegistry;
import org.talend.sdk.component.runtime.manager.ParameterMeta;
import org.talend.sdk.component.runtime.manager.reflect.MigrationHandlerFactory;
import org.talend.sdk.component.runtime.manager.reflect.ParameterModelService;
import org.talend.sdk.component.runtime.manager.reflect.ReflectionService;
import org.talend.sdk.component.runtime.manager.reflect.parameterenricher.BaseParameterEnricher;
import org.talend.sdk.component.runtime.manager.spi.ContainerListenerExtension;
import org.talend.sdk.component.runtime.manager.util.Lazy;
import org.talend.sdk.component.runtime.manager.xbean.registry.EnrichedPropertyEditorRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Slf4j
public class AutoPollingExtension implements ContainerListenerExtension {

    @Override
    public void onCreate(Container container) {
        log.info("1 XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX AutoPollingExtension.onCreate");
        Optional.ofNullable(container.get(ContainerComponentRegistry.class)).ifPresent(r -> registerPollingMappers(r, container));
    }

    @Override
    public void onClose(Container container) {
        // Do nothing
        log.info("3 XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX AutoPollingExtension.onClose");
    }

    private void registerPollingMappers(final ContainerComponentRegistry registry, final Container container) {
        log.info("2 XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX AutoPollingExtension.registerPollingMappers");
        registry.getComponents().values().forEach(family -> {
            final Map<String, ComponentFamilyMeta.PartitionMapperMeta> mappers = family.getPartitionMappers();
            final List<ComponentFamilyMeta.PartitionMapperMeta> pollables = mappers.values().stream().filter(this::isPollable)
                    .collect(Collectors.toList());

            // pollables.stream().forEach(mapperMeta -> log.info("===> " + mapperMeta.getType().getName() + " / " +
            // mapperMeta.getName() + " :: " + mapperMeta.toString()));

            if (!pollables.isEmpty()) {
                mappers.putAll(pollables.stream().map(p -> toPollable(p, container))
                        .collect(Collectors.toMap(ComponentFamilyMeta.PartitionMapperMeta::getName, Function.identity())));
            }
        });
    }

    private boolean isPollable(final ComponentFamilyMeta.PartitionMapperMeta value) {
        // return value.getType().isAnnotationPresent(Pollable.class);

        // not infinite && Pollable
        return true;
    }

    private ComponentFamilyMeta.PartitionMapperMeta toPollable(final ComponentFamilyMeta.PartitionMapperMeta meta,
                                                               final Container container) {
        final String pollingName = meta.getName() + "Polling";
        final boolean infinite = true;

        log.info("XXXXX Clone : change name " + meta.getName() + " -> " + pollingName);

        final Supplier<List<ParameterMeta>> batchMetasSupplier = Lazy.lazy(() -> {

            final LocalConfiguration localConfig = LocalConfiguration.class.cast(container.get(ComponentManager.AllServices.class).getServices().get(LocalConfiguration.class));
            final List<ParameterMeta> originalMetas = meta.getParameterMetas().get();
            final List<ParameterMeta> root = originalMetas.stream()
                    .filter(it -> !it.getName().startsWith("$"))
                    .collect(toList());
            if (root.size() != 1) {
                throw new IllegalArgumentException("Didn't find root configuration object for: " + meta.getName());
            }

            final String rootPath = root.iterator().next().getPath();
            final List<ParameterMeta> metas = getPoolingRawMetas(localConfig).stream()
                    .map(pollingMeta -> new ParameterMeta(
                            pollingMeta.getSource(),
                            pollingMeta.getJavaType(),
                            pollingMeta.getType(),
                            rootPath + '.' + pollingMeta.getPath(),
                            pollingMeta.getName(),
                            pollingMeta.getI18nPackages(),
                            pollingMeta.getNestedParameters(),
                            pollingMeta.getProposals(),
                            pollingMeta.getMetadata(),
                            pollingMeta.isLogMissingResourceBundle()))
                    .collect(toList());
            return originalMetas.stream()
                    .map(it -> it == root ?
                            new ParameterMeta(
                                    it.getSource(),
                                    it.getJavaType(),
                                    it.getType(),
                                    it.getPath(),
                                    it.getName(),
                                    it.getI18nPackages(),
                                    Stream.concat(it.getNestedParameters().stream(), metas.stream())
                                            .collect(toList()),
                                    it.getProposals(),
                                    it.getMetadata(),
                                    it.isLogMissingResourceBundle()) :
                            it)
                    .collect(toList());
        });

        final int version = meta.getVersion(); // todo
        final Function<Map<String, String>, Mapper> instantiator = config -> {
            final String pollingConfigPrefix = "configuration.internal_polling_configuration";
            final Map<String, String> pollingConfig = config.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(pollingConfigPrefix))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            final Map<String, String> batchConfig = new HashMap<>(config);
            batchConfig.keySet().removeAll(pollingConfig.keySet());
            final Mapper batchMapper = meta.getInstantiator().apply(batchConfig);

            // todo: getPoolingRawMetas ne devrait être appelr qu'une seule fois
            final LocalConfiguration localConfig = LocalConfiguration.class.cast(container.get(ComponentManager.AllServices.class).getServices().get(LocalConfiguration.class));
            final List<ParameterMeta> pollingMetas = getPoolingRawMetas(localConfig);
            final EnrichedPropertyEditorRegistry registry = new EnrichedPropertyEditorRegistry();
            final Function<Map<String, String>, Object[]> pollingConfigFactory;
            try {
                pollingConfigFactory = new ReflectionService(new ParameterModelService(registry), registry)
                        .parameterFactory(AutoPollingExtension.class.getMethod("methodWithPollingConfigurationOption", PollingConfiguration.class),
                                container.get(ComponentManager.AllServices.class).getServices(),
                                pollingMetas);
            } catch (final NoSuchMethodException e) { // unlikely
                throw new IllegalStateException(e);
            }
            final PollingConfiguration pollingConfiguration = PollingConfiguration.class.cast(pollingConfigFactory.apply(pollingConfig)[0]);

            return new PollingMapper(pollingConfiguration, batchMapper);
        };

        //todo: extraire tous les new XXXXXService
        final MigrationHandler pollingMigrationHandler = new MigrationHandlerFactory(new ReflectionService(new ParameterModelService(new EnrichedPropertyEditorRegistry()), new EnrichedPropertyEditorRegistry()))
                .findMigrationHandler(null/*polling metas*/, PollingConfiguration.class, container.get(ComponentManager.AllServices.class));
        final Supplier<MigrationHandler> migrationHandler = meta.getMigrationHandler(); // todo: split the configuration in polling and batch to have 2 migrations as for the instantiator

        ComponentFamilyMeta.PartitionMapperMeta clone = new ComponentFamilyMeta.PartitionMapperMeta(meta.getParent(),
                meta.getName() + "Polling", meta.getIcon(), version, meta.getType(), batchMetasSupplier,
                instantiator, Lazy.lazy(() -> (MigrationHandler) (incomingVersion, incomingData) -> {
                    // migrate batch
                    // migrate polling
                    // merge config
                    // todo: return mergedConfig;
                    return incomingData;
                }), meta.isValidated(), infinite) {
            // Just work around since constructor is protected
            // Change name
            // force infinite=true
        };

        return clone;
    }

    private List<ParameterMeta> getPoolingRawMetas(final LocalConfiguration localConfig) {
        try {
            return new ParameterModelService(new EnrichedPropertyEditorRegistry()) // todo: maybe use ComponentManager.instance().getParameterModelService()
                    .buildParameterMetas(AutoPollingExtension.class.getMethod("methodWithPollingConfigurationOption", PollingConfiguration.class),
                            PollingConfiguration.class.getPackage().getName(), new BaseParameterEnricher.Context(localConfig));
        } catch (final NoSuchMethodException e) { // unlikely, means methodALacon is not there
            throw new IllegalStateException(e);
        }
    }

    public void methodWithPollingConfigurationOption(@Option("internal_polling_configuration") final PollingConfiguration pollingConfiguration) {
        // no-op
    }
}