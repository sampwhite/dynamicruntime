package org.dynamicruntime.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class CacheMap<U,V> extends LinkedHashMap<U,V> {
    public final int maxItems;

    public CacheMap(int maxItems, boolean accessOrder) {
        super(maxItems, 0.75f, accessOrder);
        this.maxItems = maxItems;
    }
    @Override
    public boolean removeEldestEntry(Map.Entry<U,V> entry) {
        return size() > maxItems;
    }
}