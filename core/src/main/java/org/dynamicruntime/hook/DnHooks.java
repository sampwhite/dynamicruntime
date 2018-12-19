package org.dynamicruntime.hook;

import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("WeakerAccess")
public class DnHooks {
    public final Map<DnHookTypeInterface, DnHook> hooks = new ConcurrentHashMap<>();

    public <T> DnHook<T> getHook(DnHookTypeInterface<T> key) {
        var hook = getHookDirect(key);
        if (hook == null || hook.hasNewChanges) {
            synchronized (hooks) {
                hook = getHookDirect(key);
                if (hook == null) {
                    hook = new DnHook<>(mList());
                } else if (hook.hasNewChanges) {
                    hook = hook.mkUpdate();
                }
                hooks.put(key, hook);
            }
        }
        return hook;
    }

    @SuppressWarnings("unchecked")
    public <T> DnHook<T> getHookDirect(DnHookTypeInterface<T> key) {
        return hooks.get(key);
    }
}
