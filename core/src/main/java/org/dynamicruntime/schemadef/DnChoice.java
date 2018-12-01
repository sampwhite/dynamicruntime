package org.dynamicruntime.schemadef;

import org.dynamicruntime.util.DnCollectionUtil;

import java.util.Collections;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnChoice {
    // If this is a numeric type, it will have to be parsed.
    public final String choice;
    public final String label;
    public final String description;
    public final Map<String,Object> data;

    public DnChoice(String choice, String label, String description, Map<String,Object> data) {
        this.choice = choice;
        this.label = label;
        this.description = description;
        this.data = Collections.unmodifiableMap(DnCollectionUtil.cloneMap(data));
    }
}
