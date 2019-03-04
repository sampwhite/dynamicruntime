package org.dynamicruntime.common.user;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.function.DnFunction;
import org.dynamicruntime.util.CacheMap;
import org.dynamicruntime.util.DatedCacheMap;

import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.util.Date;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class UserCache {

    public final DatedCacheMap<Long,AuthUserRow> authCache = new DatedCacheMap<>(500);
    public final DatedCacheMap<Long,Map<String,Object>> profileCache = new DatedCacheMap<>(500);
    public final DatedCacheMap<String,AuthUserRow> tokenCache = new DatedCacheMap<>(20);

    public AuthUserRow getAuthUserRow(long id, int timeoutSeconds,
            DnFunction<DatedCacheMap.DatedItem<AuthUserRow>,AuthUserRow> createItem)
            throws DnException {
        return authCache.getItem(id, timeoutSeconds, true, createItem);
    }

    public Map<String,Object> getProfileData(long id, int timeoutSeconds,
            DnFunction<DatedCacheMap.DatedItem<Map<String,Object>>,Map<String,Object>> createItem) throws DnException {
        return profileCache.getItem(id, timeoutSeconds, true, createItem);
    }

    /** For a particular token (authId + tokenData), caches successful results only. */
    public AuthUserRow getAuthDataByToken(String tokenKey, int timeoutSeconds,
            DnFunction<DatedCacheMap.DatedItem<AuthUserRow>,AuthUserRow> createItem) throws DnException {
        return tokenCache.getItem(tokenKey, timeoutSeconds, false, createItem);
    }

    /** Used for testing to allow modification to user data and get immediate results. */
    public <U,V>void clearCache(CacheMap<U,V> cache) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (cache) {
            cache.clear();
        }
    }
}
