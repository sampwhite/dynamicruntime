package org.dynamicruntime.context;

import org.jetbrains.annotations.NotNull;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.context.DnCxtConstants.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"WeakerAccess", "unused"})
public class DnCxt {
    public String cxtName;
    public final InstanceConfig instanceConfig;
    public final List<String> parentLoggingIds;
    public final String loggingId;
    /** The storage shard. Used by unit tests to isolate in-memory data from each other. Also
     * used for scalability to do full stack software sharding of CPU and data. */
    public final String shard;
    /** Objects that can be attached to this context. It is assumed that only one thread will
     * ever access this object. If the DnCxt is needs to be used by a different thread, then
     * {@link #mkSubContext} should be used to create a sub context. Note that it is assumed
     * that the values interior to the object stored in this map are assumed to be immutable
     * or are thread-safe. */
    public final Map<String,Object> locals = mMap();
    public final Date creationDate = new Date();

    public DnCxt(@NotNull String cxtName, InstanceConfig instanceConfig, DnCxt parentCxt, String shard) {
        var ic = instanceConfig;
        if (ic == null) {
            ic = new InstanceConfig("codeTest", UNIT, TEST_TYPE);
        }
        this.cxtName = cxtName;
        this.instanceConfig = ic;
        List<String> l = parentCxt != null ? cloneList(parentCxt.parentLoggingIds) : mList();
        if (parentCxt != null) {
            l.add(parentCxt.loggingId);
        }
        parentLoggingIds = l;

        this.loggingId = cxtName + ic.getNextLoggingId();
        this.shard = shard != null ? shard : PRIMARY;
    }

    public static DnCxt mkSimpleCxt(String cxtName) {
        return new DnCxt(cxtName, null, null, null);
    }

    public DnCxt mkSubContext(@NotNull String subCxtName) {
        var subCxt = new DnCxt(subCxtName, this.instanceConfig, this, this.shard);
        subCxt.locals.putAll(this.locals);
        return subCxt;
    }
}
