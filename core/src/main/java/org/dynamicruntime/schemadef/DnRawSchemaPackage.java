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

    public DnRawSchemaPackage addTypes(List<DnRawTypeInterface> types) {
        var typesToAdd = new ArrayList<DnRawType>();
        for (var type : types) {
            var rawType = type.getRawType();
            rawType.finish();
            typesToAdd.add(rawType);
        }
        rawTypes.addAll(typesToAdd);
        return this;
    }

    public static DnRawSchemaPackage mkPackage(String packageName, String namespace,
            List<DnRawTypeInterface> types) {
        return new DnRawSchemaPackage(packageName, namespace).addTypes(types);
    }
}
