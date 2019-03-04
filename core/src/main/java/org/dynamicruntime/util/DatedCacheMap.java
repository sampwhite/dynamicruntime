package org.dynamicruntime.util;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.function.DnFunction;

import java.util.Date;

@SuppressWarnings("WeakerAccess")
public class DatedCacheMap<U,V> {
    public final CacheMap<U,DatedItem<V>> cache;
    public static class DatedItem<T> {
        public final T item;
        public final Date dateCached;

        public DatedItem(T item, Date dateCached) {
            this.item = item;
            this.dateCached = dateCached;
        }
    }

    public DatedCacheMap(int maxItems) {
        this.cache = new CacheMap<>(maxItems, true);
    }

    public V getItem(U id,  int timeoutSeconds, boolean keepEmpty,
            DnFunction<DatedItem<V>, V> createItem) throws DnException {
        Date now = new Date();
        synchronized (cache) {
            DatedItem<V> curVal = cache.get(id);
            V item;
            if (curVal == null || curVal.dateCached.getTime() + timeoutSeconds * 1000 < now.getTime()) {
                V newItem = createItem.apply(curVal);
                if (newItem != null || keepEmpty) {
                    curVal = new DatedItem<>(newItem, now);
                    cache.put(id, curVal);
                }
                item = newItem;
            } else {
                item = curVal.item;
            }
            return item;
        }
    }


    public void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }

}
