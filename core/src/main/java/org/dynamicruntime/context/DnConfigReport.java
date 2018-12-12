package org.dynamicruntime.context;

import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnConfigReport {
    public final String key;
    public final Object val;
    public final String description;
    public final Object dflt;
    public final Map<String,Object> extras;

    public DnConfigReport(String key, Object val, String description, Object dflt, Map<String,Object> extras) {
        this.key = key;
        this.val = val;
        this.description = description;
        this.dflt = dflt;
        this.extras = extras;
    }
}
