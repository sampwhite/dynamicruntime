package org.dynamicruntime.schemadef;

import org.dynamicruntime.util.ConvertUtil;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.util.List;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnTypeUtils {
    public static String applyNamespace(String namespace, String dnTypeName) {
        if (namespace == null || namespace.length() == 0) {
            return dnTypeName;
        }
        if (dnTypeName.indexOf('.') >= 0 || DnSchemaDefConstants.isPrimitive(dnTypeName)) {
            return dnTypeName;
        }
        return namespace + "." + dnTypeName;
    }

    public static Map<String,Object> updateTypeIfChanged(String namespace, String dnTypeKey, Map<String,Object> data,
            boolean cloneDataOnChange) {
        if (namespace == null || namespace.length() == 0) {
            return null;
        }
        String typeName = ConvertUtil.getOptStr(data, dnTypeKey);
        if (typeName == null) {
            return null;
        }
        if (typeName.indexOf('.') >= 0 || DnSchemaDefConstants.isPrimitive(typeName)) {
            return null;
        }

        var m = data;
        if (cloneDataOnChange) {
            m = cloneMap(data);
        }
        m.put(dnTypeKey,namespace + "." + typeName);
        return m;
    }

    public static Map<String,Object> updateTypesIfChanged(String namespace, String dnTypeRefsKey, Map<String,Object> data,
            boolean cloneDataOnChange) {
        if (namespace == null || namespace.length() == 0) {
            return null;
        }
        List<String> typeRefs = ConvertUtil.getOptListOfStrings(data, dnTypeRefsKey);
        if (typeRefs == null) {
            return null;
        }
        List<String> newTypes = mList();
        boolean didChange = false;
        for (String typeRef : typeRefs) {
            if (typeRef.indexOf('.') < 0) {
                didChange = true;
                newTypes.add(namespace + "." + typeRef);
            } else {
                newTypes.add(typeRef);
            }
        }
        if (!didChange) {
            return null;
        }

        var m = data;
        if (cloneDataOnChange) {
            m = cloneMap(data);
        }
        m.put(dnTypeRefsKey, newTypes);
        return m;
    }

}
