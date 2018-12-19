package org.dynamicruntime.hook;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;

public interface DnHookFunction<T,U,V> {
    default boolean execute(DnCxt cxt, T parent, U input, V workData) throws DnException {
        return false;
    }

    void notify(DnCxt cxt, T parent, U input, V workData) throws DnException;
}
