package org.dynamicruntime.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("WeakerAccess")
public class InstanceConfig {
    public final AtomicInteger loggingIdCount = new AtomicInteger(0);
    public final String instanceName;
    public final String envName;
    public final String envType;

    private Map<String,Object> config = new ConcurrentHashMap<>();

    // A report on configuration strings retrieved.
    public final Map<String,DnConfigReport> configAccessReport = new ConcurrentHashMap<>();

    public InstanceConfig(String instanceName, String envName, String envType) {
        this.instanceName = instanceName;
        this.envName = envName;
        this.envType = envType;
    }

    public Object get(String key) {
        return config.get(key);
    }

    public void put(String key, Object val) {
        config.put(key, val);
    }
    public void putAll(Map<String,Object> configs) {
        config.putAll(configs);
    }

    public int getNextLoggingId() {
        int n = loggingIdCount.incrementAndGet();
        if (n > 1000000000) {
            loggingIdCount.compareAndSet(n, 0);
        }
        return n;
    }
}
