package org.dynamicruntime.startup;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.InstanceConfig;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnRawSchemaStore;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.context.DnCxtConstants.*;
import static org.dynamicruntime.startup.LogStartup.*;

import java.util.*;

@SuppressWarnings("WeakerAccess")
public class InstanceRegistry {
    // This is global to the VM. However, different instances can choose which of these components are loaded or
    // active.
    static private final Map<String,ComponentDefinition> componentDefinitions = new LinkedHashMap<>();
    static private final Map<String, InstanceConfig> instanceConfigs = mMapT();
    // These will get changed during initialization process. But once the first instance has been created,
    // these values should not change and will be global to all instances.
    static public String envName = DEV;
    static public String envType = GENERAL_TYPE;

    // Call only during initialization of VM.
    public static void addComponentDefinitions(List<ComponentDefinition> definitions) {
        for (ComponentDefinition definition : definitions) {
            componentDefinitions.put(definition.getComponentName(), definition);
        }
    }

    public static DnCxt createCxt(String cxtName, InstanceConfig config) {
        return new DnCxt(cxtName, config, null, null);
    }

    public static InstanceConfig getOrCreateInstanceConfig(String instanceName, Map<String,Object> overlayConfig)
        throws DnException {
        return getOrCreateInstanceConfig(instanceName, overlayConfig, componentDefinitions.values());
    }

    public static InstanceConfig getOrCreateInstanceConfig(String instanceName, Map<String,Object> overlayConfig,
                Collection<ComponentDefinition> suppliedCompDefs) throws DnException {
        synchronized (instanceConfigs) {
            var curConfig = instanceConfigs.get(instanceName);
            if (curConfig != null) {
                return curConfig;
            }

            // Into interesting case.
            InstanceConfig config = new InstanceConfig(instanceName, envName, envType);
            config.putAll(overlayConfig);

            // We have enough to create a DnCxt.
            var cxt = new DnCxt("startup", config, null, null);
            log.info(cxt, String.format("Initializing instance '%s'.", instanceName));

            // Later, we would read in various config files and allow them to control our initialization.
            // ... Load config ... Apply overlayConfig again ... prepare for components ...

            // Create DnRawSchemaStore and get components to load in their schema packages or modify existing
            // schemas.
            var rawSchemaStore = new DnRawSchemaStore();
            config.put(DnRawSchemaStore.DN_RAW_SCHEMA_STORE, rawSchemaStore);

            // Use sorted version of components.
            var compDefs = cloneList(suppliedCompDefs);
            compDefs.sort(Comparator.comparingInt(ComponentDefinition::loadPriority));

            for (var definition : compDefs) {
                // Register DnRawSchemaPackage and DnEndpointFunction objects.
                definition.addSchema(cxt, rawSchemaStore);
            }

            // Collect component service initializers.
            List<Class> startupInitializers = mList();
            List<Class> serviceInitializers = mList();

            for (var definition : compDefs) {
                startupInitializers.addAll(definition.getStartupInitializers(cxt));
                serviceInitializers.addAll(definition.getServiceInitializers(cxt));
            }

            bindAndInitServices(cxt, startupInitializers);
            bindAndInitServices(cxt, serviceInitializers);

            return config;
        }

    }

    @SuppressWarnings("unchecked")
    public static void bindAndInitServices(DnCxt cxt, Collection<Class> initializersList) throws DnException {
        var instance = cxt.instanceConfig;
        List<ServiceInitializer> services = mList();
        for (Class initializer : initializersList) {
            try {
                var service = initializer.getConstructor().newInstance();
                if (!(service instanceof ServiceInitializer)) {
                    throw new DnException("Class " + initializer.getCanonicalName() + " did not implement a " +
                            "ServiceInitializer interface.");
                }
                var serviceInitializer = (ServiceInitializer)service;
                services.add(serviceInitializer);
            } catch (ReflectiveOperationException e) {
                throw new DnException("Could not instantiate service " + initializer.getCanonicalName() + ".", e);
            }
        }

        for (ServiceInitializer service : services) {
            service.onCreate(cxt);
            instance.put(service.getServiceName(), service);
        }
        for (ServiceInitializer service : services) {
            service.checkInit(cxt);
        }
        for (ServiceInitializer service : services) {
            service.checkReady(cxt);
        }
    }


}
