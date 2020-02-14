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
import org.apache.xbean.propertyeditor.PropertyEditorRegistry;
import org.talend.sdk.component.api.component.MigrationHandler;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.service.configuration.LocalConfiguration;
import org.talend.sdk.component.api.service.injector.Injector;
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
        registry.getComponents().values().forEach(family -> {                                                                   // For each family
            final Map<String, ComponentFamilyMeta.PartitionMapperMeta> mappers = family.getPartitionMappers();                  // We retrieve PartitionMappers (input connectors)
            final List<ComponentFamilyMeta.PartitionMapperMeta> pollables = mappers.values().stream().filter(this::isPollable)  // And filter to retrieve only pollable partition mapper
                    .collect(Collectors.toList());

            if (!pollables.isEmpty()) {                                                                                         // If any pollabke partition mapper
                mappers.putAll(pollables.stream().map(p -> toPollable(p, container))                                            // We add/register a duplicate of the mapper transformed to a pollable
                        .collect(Collectors.toMap(ComponentFamilyMeta.PartitionMapperMeta::getName, Function.identity())));
            }
        });
    }

    /**
     * @param partitionMapperMeta Meta information of a partition mapper.
     * @return true if the given partition mapper is pollable if it is a batch input (infinite = false) and has the Pollable annotation.
     */
    private boolean isPollable(final ComponentFamilyMeta.PartitionMapperMeta partitionMapperMeta) {
        Optional<Annotation> pollable = Stream.of(partitionMapperMeta.getType().getAnnotations()) //
                .filter(a -> a.annotationType().equals(Pollable.class)) //
                .findFirst();

        return !partitionMapperMeta.isInfinite() && pollable.isPresent();
    }

    private ComponentFamilyMeta.PartitionMapperMeta toPollable(final ComponentFamilyMeta.PartitionMapperMeta batchMetas,
                                                               final Container container) {
        final String pollingName = batchMetas.getName() + "Polling"; // Rename the duplicated mapper to namePolling
        final boolean infinite = true; // Polling connectors are infinite = true

        final Map<Class<?>, Object> services = container.get(ComponentManager.AllServices.class).getServices();
        final LocalConfiguration localConfig = LocalConfiguration.class.cast(services.get(LocalConfiguration.class));
        final Injector injector = Injector.class.cast(services.get(Injector.class));
        final PropertyEditorRegistry propertyEditorRegistry = PropertyEditorRegistry.class.cast(services.get(PropertyEditorRegistry.class));
        final AtomicReference<List<ParameterMeta>> metas = new AtomicReference<>();
        final Supplier<List<ParameterMeta>> aggregatedMetasSupplier = Lazy.lazy(() -> {
            final List<ParameterMeta> originalMetas = batchMetas.getParameterMetas().get();

            final List<ParameterMeta> root = originalMetas.stream()
                    .filter(it -> !it.getName().startsWith("$"))  // root configuration should be the only metadata which doesn't start by '$'
                    .collect(toList());
            if (root.size() < 1) {
                throw new IllegalArgumentException("Can't find any root configuration object for: " + batchMetas.getName());
            }
            else if(root.size() > 1){
                throw new IllegalArgumentException("Several root configuration object have been found for: " + batchMetas.getName());
            }

            final String rootPath = root.iterator().next().getPath();

            List<ParameterMeta> parameterMetas = metas.get();
            if (parameterMetas == null) {
                parameterMetas = getPoolingRawMetas(localConfig, propertyEditorRegistry, injector, batchMetas.getType().getPackage());
                metas.compareAndSet(null, parameterMetas);
            }
            final List<ParameterMeta> poolingMetas = parameterMetas.stream()
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

            return originalMetas.stream()  // Return a copy of metas of original connector ...
                    .map(it -> it == root ?
                            new ParameterMeta(
                                    it.getSource(),
                                    it.getJavaType(),
                                    it.getType(),
                                    it.getPath(),
                                    it.getName(),
                                    it.getI18nPackages(),
                                    Stream.concat(it.getNestedParameters().stream(), poolingMetas.stream()) // ... agregated with the polling ones
                                            .collect(toList()),
                                    it.getProposals(),
                                    it.getMetadata(),
                                    it.isLogMissingResourceBundle()) :
                            it)
                    .collect(toList());
        }); // And aggregatedMetasSupplier

        final int version = batchMetas.getVersion(); // todo
        final Function<Map<String, String>, Mapper> instantiator = config -> {
            final String pollingConfigPrefix = "configuration.internal_polling_configuration";
            final Map<String, String> pollingConfig = config.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(pollingConfigPrefix))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            final Map<String, String> batchConfig = new HashMap<>(config);
            batchConfig.keySet().removeAll(pollingConfig.keySet());
            final Mapper batchMapper = batchMetas.getInstantiator().apply(batchConfig);

            List<ParameterMeta> pollingMetas = metas.get();
            if (pollingMetas == null) {
                pollingMetas = getPoolingRawMetas(localConfig, propertyEditorRegistry, injector, batchMetas.getType().getPackage());
                metas.compareAndSet(null, pollingMetas);
            }
            final EnrichedPropertyEditorRegistry registry = new EnrichedPropertyEditorRegistry();
            final Function<Map<String, String>, Object[]> pollingConfigFactory;
            try {
                pollingConfigFactory = new ReflectionService(new ParameterModelService(registry), registry)
                        .parameterFactory(AutoPollingExtension.class.getMethod("methodWithPollingConfigurationOption", PollingConfiguration.class),
                                services,
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
        final Supplier<MigrationHandler> migrationHandler = batchMetas.getMigrationHandler(); // todo: split the configuration in polling and batch to have 2 migrations as for the instantiator

        ComponentFamilyMeta.PartitionMapperMeta clone = new ComponentFamilyMeta.PartitionMapperMeta(batchMetas.getParent(),
                pollingName, batchMetas.getIcon(), version, batchMetas.getType(), aggregatedMetasSupplier,
                instantiator, Lazy.lazy(() -> (MigrationHandler) (incomingVersion, incomingData) -> {
            // migrate batch
            // migrate polling
            // merge config
            // todo: return mergedConfig;
            return incomingData;
        }), batchMetas.isValidated(), infinite) {
            // Just work around since constructor is protected
            // Change name
            // force infinite=true
        };

        return clone;
    }

    /**
     * Return metadata of the @option of the method methodWithPollingConfigurationOption.
     * It means the metadata of the specific configuration of the polling.
     * @param localConfig
     * @return
     */
    private List<ParameterMeta> getPoolingRawMetas(final LocalConfiguration localConfig, final PropertyEditorRegistry propertyEditorRegistry,
                                                   final Injector injector, final Package i18nPackage) {
        try {
            final ParameterModelService pms = get(get(injector, "reflectionService", ReflectionService.class), "parameterModelService", ParameterModelService.class);
            return pms.buildParameterMetas(
                                    AutoPollingExtension.class.getMethod("methodWithPollingConfigurationOption", PollingConfiguration.class),
                    i18nPackage == null ? "" : i18nPackage.getName(),
                                    new BaseParameterEnricher.Context(localConfig)
                            );
        } catch (final Exception e) {
            // Means that methodWithPollingConfigurationOption has not been found
            throw new IllegalStateException(e);
        }
    }

    private <T> T get(final Object from, final String name, final Class<T> type) {
        try {
            final Field reflectionService = from.getClass().getDeclaredField(name);
            if (!reflectionService.isAccessible()) {
                reflectionService.setAccessible(true);
            }
            return type.cast(reflectionService.get(from));
        } catch (final Exception e) {
            throw new IllegalStateException("Incompatible component runtime manager", e);
        }
    }


    // This method exists only to have the @Option with the PollingConfiguration class
    public void methodWithPollingConfigurationOption(@Option("internal_polling_configuration") final PollingConfiguration pollingConfiguration) {
        // no-op
    }
}