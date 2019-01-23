package org.dynamicruntime.node;

import java.util.Map;

import static org.dynamicruntime.util.DnCollectionUtil.*;

@SuppressWarnings("WeakerAccess")
public class DnNodeData {
    /** Values are set only at initialization time, so we do not need to synchronize. */
    protected final Map<String,DnAuthConfig> authConfigMap = mMapT();

    public DnAuthConfig getAuthConfig(String key) {
        return authConfigMap.get(key);
    }

    public void putAuthConfig(String key, DnAuthConfig authConfig) {
        authConfigMap.put(key, authConfig);
    }

}
