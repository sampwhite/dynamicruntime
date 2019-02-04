package org.dynamicruntime.startup;

import org.dynamicruntime.config.ConfigConstants;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.InstanceConfig;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnRawSchemaStore;
import org.dynamicruntime.config.ConfigLoadUtil;
import org.dynamicruntime.util.ConvertUtil;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.context.DnCxtConstants.*;
import static org.dynamicruntime.startup.LogStartup.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("WeakerAccess")
public class InstanceRegistry {
    // Change if want a different default instance.
    static public String defaultInstance = "local";
    static public boolean isFirstTime = true;

    // This is global to the VM. However, different instances can choose which of these components are loaded or
    // active.
    static private final Map<String,ComponentDefinition> componentDefinitions = new LinkedHashMap<>();
    static private final Map<String, InstanceConfig> instanceConfigs = new ConcurrentHashMap<>();
    // These get changed during the initialization process. But once the first instance has been created,
    // these values should not change and are global to all instances. We default to assuming
    // code is running in-memory databases doing unit tests.
    static public String envName = UNIT;
    static public String envType = TEST_TYPE;

    public static void setDefaultInstance(String instance) {
        defaultInstance = instance;
    }

    public static void setEnvName(String newEnvName) {
        envName = newEnvName;
    }

    @SuppressWarnings("unused")
    public static void setEnvType(String newEnvType) {
        envType = newEnvType;
    }

    public static void setDevMode() {
        envName = DEV;
        envType =  GENERAL_TYPE;
    }

    // Call only during initialization of VM (and it is assumed at this point that the start up is single threaded).
    public static void addComponentDefinitions(List<ComponentDefinition> definitions) {
        synchronized (componentDefinitions) {
            if (isFirstTime) {
                isFirstTime = false;
                doVmInit();
            }
            for (ComponentDefinition definition : definitions) {
                String cName = definition.getComponentName();
                if (!componentDefinitions.containsKey(cName)) {
                    componentDefinitions.put(definition.getComponentName(), definition);
                }
            }
        }
    }

    public static void doVmInit() {
        Runtime.getRuntime().addShutdownHook(new DnShutdownThread());
    }

    public static DnCxt createCxt(String cxtName, String instanceName) throws DnException {
        InstanceConfig config = getOrCreateInstanceConfig(instanceName, mMap());
        return createCxt(cxtName, config);
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
        // Do a quick test to see if it exists (outside synchronization lock).
        var cc = instanceConfigs.get(instanceName);
        if (cc != null) {
             return cc;
        }

        synchronized (instanceConfigs) {
            var curConfig = instanceConfigs.get(instanceName);
            if (curConfig != null) {
                return curConfig;
            }

            // Into interesting case. Eventually we will use system properties or AWS configuration
            // to drive envType and envName settings.
            String configEnvType = ConvertUtil.getOptStr(overlayConfig, ConfigConstants.ENV_TYPE);
            configEnvType = (configEnvType != null) ? configEnvType : envType;
            InstanceConfig config = new InstanceConfig(instanceName, envName, configEnvType);

            // Overlay config is used twice: once during the initialization of components and then
            // applied again at the end.
            var flattenedOverlay = collapseMaps(overlayConfig);
            config.putAll(flattenedOverlay);

            // We have enough to create a DnCxt.
            var cxt = new DnCxt("startup", config, null, null);
            log.info(cxt, String.format("Initializing instance '%s'.", instanceName));

            if (!config.envName.equals(UNIT)) {
                // Search for file that has secrets. This allows us to have much of our configuration
                // stored as source code files. In particular, database passwords are given
                // by a lookup key that extract it from the private data. Eventually we will
                // also have code to pull secrets from AWS.
                Map<String,Object> privateData = ConfigLoadUtil.findAndReadYamlFile(cxt, "private/dnConfig.yaml");
                if (privateData != null) {
                    cxt.instanceConfig.putAll(collapseMaps(privateData));
                } else {
                    LogStartup.log.info(cxt, "Application is starting up in in memory simulation mode " +
                            "because relative path *private/dnConfig.yaml* could not be found.");
                    cxt.instanceConfig.put(ConfigConstants.IN_MEMORY_SIMULATION, true);
                }
            } else {
                cxt.instanceConfig.put(ConfigConstants.IN_MEMORY_SIMULATION, true);
            }

            // Create DnRawSchemaStore and get components to load in their schema packages or modify existing
            // schemas.
            var rawSchemaStore = new DnRawSchemaStore();
            config.put(DnRawSchemaStore.DN_RAW_SCHEMA_STORE, rawSchemaStore);

            // Use sorted version of components.
            var compDefs = cloneList(suppliedCompDefs);
            compDefs.sort(Comparator.comparingInt(ComponentDefinition::loadPriority));

            var configData = mMap();

            for (var definition : compDefs) {
                // Register DnRawSchemaPackage and DnEndpointFunction objects.
                if (definition.isLoaded()) {
                    definition.addSchema(cxt, rawSchemaStore);
                }
                // Load config of active components. It is the config that turns
                // on optional features in the component.
                if (definition.isActive()) {
                    String configFile = definition.getConfigFileName();
                    if (configFile != null) {
                        var compData = ConfigLoadUtil.parseYamlResource(configFile);
                        configData.putAll(compData);
                    }
                }
            }

            // Apply overlay config last.
            mergeMapRecursively(configData, overlayConfig);

            // Resolve config and set it as the instance config. Resolving performs
            // some complicated activities; look at implementation.
            var resolvedConfig = ConfigLoadUtil.resolveConfig(cxt, configData);
            config.putAll(resolvedConfig);

            // Collect component service initializers.
            List<Class> startupInitializers = mList();
            List<Class> serviceInitializers = mList();

            for (var definition : compDefs) {
                if (definition.isLoaded()) {
                    var defStartup = definition.getStartupInitializers(cxt);
                    if (defStartup != null) {
                        startupInitializers.addAll(defStartup);
                    }
                    var defService = definition.getServiceInitializers(cxt);
                    if (defService != null) {
                        serviceInitializers.addAll(definition.getServiceInitializers(cxt));
                   }
                }
            }

            bindAndInitServices(cxt, startupInitializers, true);
            bindAndInitServices(cxt, serviceInitializers, false);
            instanceConfigs.put(instanceName, config);

            return config;
        }

    }

    @SuppressWarnings("unchecked")
    public static void bindAndInitServices(DnCxt cxt, Collection<Class> initializersList, boolean isBoot) throws DnException {
        var instance = cxt.instanceConfig;
        List<String> initializerKeys = mList();
        for (Class initializer : initializersList) {
            try {
                var service = initializer.getConstructor().newInstance();
                if (isBoot) {
                    if (!(service instanceof StartupServiceInitializer)) {
                        throw new DnException("Class " + initializer.getCanonicalName() + " did not implement a " +
                                "StartupServiceInitializer interface.");
                    }
                } else {
                    if (!(service instanceof ServiceInitializer)) {
                        throw new DnException("Class " + initializer.getCanonicalName() + " did not implement a " +
                                "ServiceInitializer interface.");
                    }
                    if (service instanceof StartupServiceInitializer) {
                        throw new DnException("Class " + initializer.getCanonicalName()
                                + " is a startup initializer but it is in the regular start up list.");
                    }
                }
                var serviceInitializer = (ServiceInitializer)service;

                // Publish the service object so other services can find it and keep track of
                // keys we used.
                String serviceName = serviceInitializer.getServiceName();
                if (instance.get(serviceName) == null) {
                    initializerKeys.add(serviceName);
                }
                instance.put(serviceName, serviceInitializer);
            } catch (ReflectiveOperationException e) {
                throw new DnException("Could not instantiate service " + initializer.getCanonicalName() + ".", e);
            }
        }

        // Get the service initializers we ended up registering. This allows components to replace
        // another component's service implementation. Useful for testing.
        List<ServiceInitializer> services = nMapSimple(initializerKeys, (key ->
                (ServiceInitializer)instance.get(key)));

        // Do the initialization sequence. We give services three passes at initialization
        // so that the services can have complex dependency startup relationships.
        for (ServiceInitializer service : services) {
            service.onCreate(cxt);
        }
        for (ServiceInitializer service : services) {
            service.checkInit(cxt);
        }
        for (ServiceInitializer service : services) {
            service.checkReady(cxt);
        }
    }
}
