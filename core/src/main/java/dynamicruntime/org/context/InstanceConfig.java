package dynamicruntime.org.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static dynamicruntime.org.util.DnCollectionUtil.*;

public class InstanceConfig {
    public final AtomicInteger loggingIdCount = new AtomicInteger(0);
    public final String instanceName;
    public final String envName;
    public final String envType;
    private Map<String,Object> config = new ConcurrentHashMap<>();

    public InstanceConfig(String instanceName, String envName, String envType) {
        this.instanceName = instanceName;
        this.envName = envName;
        this.envType = envType;
    }

    public Map<String,Object> getConfig() {
        return config;
    }

    public void set(String key, Object val) {
        config.put(key, val);
    }

    public int getNextLoggingId() {
        int n = loggingIdCount.incrementAndGet();
        if (n > 1000000000) {
            loggingIdCount.compareAndSet(n, 0);
        }
        return n;
    }
}
