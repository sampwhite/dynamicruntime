package org.dynamicruntime.startup;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;

@SuppressWarnings({"RedundantThrows", "unused"})
public interface ServiceInitializer {
    String getServiceName();
    default void onCreate(DnCxt cxt) throws DnException {}
    /** Idempotent initialization method. Can be called by one service on another. */
    void checkInit(DnCxt cxt) throws DnException;
    /** Idempotent method that makes sure the service is fully ready to be used. */
    default void checkReady(DnCxt cxt) throws DnException {}
}
