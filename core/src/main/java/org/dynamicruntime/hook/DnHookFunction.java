package org.dynamicruntime.hook;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;

public interface DnHookFunction<U,V> {
    @SuppressWarnings({"unused", "SameReturnValue", "RedundantThrows"})
    default boolean execute(DnCxt cxt, U parent, V workData) throws DnException {
        return false;
    }

    void notify(DnCxt cxt, U parent, V workData) throws DnException;
}
