package org.dynamicruntime.util;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class CacheMap<U,V> extends LinkedHashMap<U,V> {
    public final int maxItems;

    /** Creates a cache map. We use built in functionality of linked hash map.
     * Normally LinkedHashMap's default order is the order that the items are put into the map.
     * However, if you set *accessOrder* to true, then any time you access an item in the
     * map it will go to the end of the internal sort. */
    public CacheMap(int maxItems, boolean accessOrder) {
        super(maxItems, 0.75f, accessOrder);
        this.maxItems = maxItems;
    }

    @Override
    public boolean removeEldestEntry(Map.Entry<U,V> entry) {
        return size() > maxItems;
    }
}