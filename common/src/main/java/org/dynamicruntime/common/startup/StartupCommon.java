package org.dynamicruntime.common.startup;

import org.dynamicruntime.CoreComponent;
import org.dynamicruntime.common.CommonComponent;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.startup.InstanceRegistry;

import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.util.Map;

public class StartupCommon {
    /** Gets the Common and Core components loaded and all the associated initialization. Does *not* start the
     * server. This can be called more than once during the running of a VM. */
    static public DnCxt mkBootCxt(String cxtName, String instanceName, Map<String,Object> extraConfig)
            throws DnException {
        InstanceRegistry.addComponentDefinitions(mList(new CoreComponent(), new CommonComponent()));
        var config = InstanceRegistry.getOrCreateInstanceConfig(instanceName, extraConfig);
        return InstanceRegistry.createCxt(cxtName, config);
    }
}
