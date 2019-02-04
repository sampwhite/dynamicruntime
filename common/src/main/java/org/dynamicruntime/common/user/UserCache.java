package org.dynamicruntime.common.user;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.function.DnFunction;
import org.dynamicruntime.util.CacheMap;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.util.Date;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class UserCache {
    public static class DatedItem<T> {
        public final T item;
        public final Date dateCached;

        public DatedItem(T item, Date dateCached) {
            this.item = item;
            this.dateCached = dateCached;
        }
    }

    public final CacheMap<Long,DatedItem<AuthUserRow>> authCache = mCacheMap(500);
    public final CacheMap<Long,DatedItem<Map<String,Object>>> profileCache = mCacheMap(500);
    public final CacheMap<String,DatedItem<AuthUserRow>> tokenCache = mCacheMap(20);

    public AuthUserRow getAuthUserRow(long id, int timeoutSeconds,
            DnFunction<DatedItem<AuthUserRow>,AuthUserRow> createItem)
            throws DnException {
        return getItem(id, authCache, timeoutSeconds, createItem);
    }

    public Map<String,Object> getProfileData(long id, int timeoutSeconds,
            DnFunction<DatedItem<Map<String,Object>>,Map<String,Object>> createItem) throws DnException {
        return getItem(id, profileCache, timeoutSeconds, createItem);
    }

    public AuthUserRow getAuthDataByToken(String tokenKey, int timeoutSeconds,
            DnFunction<DatedItem<AuthUserRow>,AuthUserRow> createItem) throws DnException {
        return getItem(tokenKey, tokenCache, timeoutSeconds, createItem);
    }

    public <U,V> V getItem(U id, CacheMap<U,DatedItem<V>> cache, int timeoutSeconds,
            DnFunction<DatedItem<V>, V> createItem) throws DnException {
        Date now = new Date();
        synchronized (cache) {
            DatedItem<V> curVal = cache.get(id);
            if (curVal == null || curVal.dateCached.getTime() + timeoutSeconds * 1000 < now.getTime()) {
                V newItem = createItem.apply(curVal);
                curVal = new DatedItem(newItem, now);
                cache.put(id, curVal);
            }
            return curVal.item;
        }
    }
}