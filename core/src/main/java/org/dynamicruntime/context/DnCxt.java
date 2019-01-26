package org.dynamicruntime.context;

import org.dynamicruntime.schemadef.DnSchemaStore;

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
    /** The current acting user for this context. Can be changed (and corresponding shard may change as well if
     * data being stored is using active user as its ). */
    public UserProfile userProfile;
    /** The active storage shard. Used by unit tests to isolate in-memory data from each other. Also
     * used for scalability to do full stack software sharding of CPU and data. This is mutable
     * so that a working thread can shift between shards, but such changes should be done with
     * great care. */
    public String shard;
    /** Objects attached to this context only and not propagated down to sub context objects. This
     * map plays the role typically played by ThreadLocal storage, but with the advantage that a
     * context can be handed off to a worker thread and not break thread local session. Sql transaction
     * sessions should be stored in this map. */
    public final Map<String,Object> session = mMap();
    /** Objects that can be attached to this context and carried down to sub context objects.
     * It is assumed that only one thread will ever access this object. If the DnCxt needs
     * to be used by more than one thread, then {@link #mkSubContext} should be used to create a
     * sub context which will clone this map. Note that it is assumed
     * that the values interior to the objects stored in this map are assumed to be immutable
     * or are thread-safe. Typical usage for this map is to cache results of data gathering and
     * computation. */
    public final Map<String,Object> locals = mMap();
    /** Used by tests to do time travel. */
    public int nowTimeOffsetInSeconds = 0;
    /** Date the context object was created.*/
    public final Date creationDate = new Date();
    /** Nano time of start time. Used to time requests. */
    public final long nanoTime = System.nanoTime();
    /** Cached copy of the schema store. Allows ready access to an unchanging read only copy of the schema. We put
     * it into the DnCxt to show how fundamental it is to the application. */
    public DnSchemaStore schemaStore;

    public DnCxt(String cxtName, InstanceConfig instanceConfig, DnCxt parentCxt, String shard) {
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

    public DnCxt mkSubContext(String subCxtName) {
        var subCxt = new DnCxt(subCxtName, this.instanceConfig, this, this.shard);
        subCxt.locals.putAll(this.locals);
        subCxt.schemaStore = schemaStore;
        subCxt.userProfile = userProfile;
        return subCxt;
    }

    public DnSchemaStore getSchema() {
        if (schemaStore == null) {
            schemaStore = DnSchemaStore.get(this);
        }
        return schemaStore;
    }

    /** Gets duration of request so far in milliseconds. */
    public double getDuration() {
        long diff = System.nanoTime() - nanoTime;
        return ((double)diff)/1000000;
    }

    public String getLogInfo() {
        String authId = (userProfile != null) ? userProfile.authId : null;
        return (authId != null) ? loggingId + "(" + authId + ")": loggingId + "%sys";
    }

    public Date now() {
        long n = System.currentTimeMillis() + 1000 * nowTimeOffsetInSeconds;
        return new Date(n);
    }
}
