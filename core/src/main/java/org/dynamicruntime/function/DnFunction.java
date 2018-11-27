package org.dynamicruntime.function;

import org.dynamicruntime.exception.DnException;

@FunctionalInterface
public interface DnFunction<U,V> {
    V apply(U var1) throws DnException;
}
