package org.dynamicruntime.schemadef;


import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class DnRawSchemaPackage {
    public final String packageName;
    public final String namespace;
    public final List<DnRawType> rawTypes = new ArrayList<>();

    public DnRawSchemaPackage(String packageName, String namespace) {
        this.packageName = packageName;
        this.namespace = namespace;
    }

    public DnRawSchemaPackage addTypes(List<DnRawType> types) {
        for (var type : types) {
            type.finish();
        }
        rawTypes.addAll(types);
        return this;
    }

    public static DnRawSchemaPackage mkPackage(String packageName, String namespace, List<DnRawType> types) {
        return new DnRawSchemaPackage(packageName, namespace).addTypes(types);
    }
}
