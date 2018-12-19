package org.dynamicruntime.hook;

import org.dynamicruntime.context.DnCxt;

public interface DnHookTypeInterface<T> {
    default void registerHookFunction(DnCxt cxt, String name, int priority,T function) {
        var hook = cxt.instanceConfig.getHook(this);
        hook.addFunctionEntry(name, priority, function);
    }
}
