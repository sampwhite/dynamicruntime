package org.dynamicruntime.schemadef;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.util.List;
import java.util.Map;

/** Holds a raw map with schema definition. Following the convention of this application, we pull out fields
 * from the map that are relevant to the operations performed using the class. This class also
 * has convenience methods designed to ease the process of creating a DnType. */
@SuppressWarnings("WeakerAccess")
public class DnRawType {
    public final String name;
    // This data can be mutated (following certain rules) either before it is added to a package
    // or after it has been cloned.
    public final Map<String,Object> model;
    public final String namespace; // Can be null if a namespace has not been applied.

    // Temporary mutable data. Used to help construction process. If this attribute becomes
    // populated, then at the time this type is added to its package, the *dnFields* attribute
    // in the model will be replaced with this data.
    public List<DnRawField> rawFields = mList();

    public DnRawType(String name, Map<String,Object> model) {
        this.name = name;
        this.model = model;
        this.namespace = StrUtil.getBeforeLastIndex(name, ".");
    }

    public static DnRawType extract(Map<String,Object> model) throws DnException {
        String name = getReqStr(model, DN_NAME);
        return new DnRawType(name, model);
    }

    /** Clones down to field data. Cloning is our main defense against data pollution across
     * schemas. Note that any anonymous types for fields get cloned, but only the parts that are
     * necessary to clone. */
    public DnRawType cloneType(String namespace) {
        Map<String,Object> newModel = cloneMap(model);

        var fields = getOptListOfMaps(newModel, DN_FIELDS);
        if (fields != null) {
            var newFields = nMapSimple(fields, (fld -> {
                // We always clone the top level field data. Allows us to change top level
                // field data as desired.
                var m = cloneMap(fld);

                if (namespace != null) {
                    DnTypeUtils.updateTypeIfChanged(namespace, DN_TYPE_REF, m, false);
                    applyNamespaceToAnonymousType(namespace, m, 0, false);
                }
                return m;
            }));
            newModel.put(DN_FIELDS, newFields);
        }
        String newName = DnTypeUtils.applyNamespace(namespace, name);
        if (!newName.equals(name)) {
            newModel.put(DN_NAME, newName);
        }
        return new DnRawType(newName, newModel);
    }

    public Map<String,Object> applyNamespaceToAnonymousType(String namespace, Map<String,Object> fieldData,
            int nestLevel, boolean cloneIfChanged) {
        if (nestLevel > 10) {
            throw new RuntimeException("Anonymous type for field " + getOptStr(fieldData, DN_NAME) +
                    " and type " + name + " nests to deeply or has a recursion in its nesting.");
        }
        Map<String,Object> anonData = getOptMap(fieldData, DN_TYPE_DEF);
        if (anonData == null) {
            return null;
        }
        List<Map<String,Object>> fields = getOptListOfMaps(anonData, DN_FIELDS);
        if (fields == null) {
            return null;
        }
        boolean[] changed = {false};
        var newFields = nMapSimple(fields, (fld -> {
            var newFld = DnTypeUtils.updateTypeIfChanged(namespace, DN_TYPE_REF, fld, true);
            if (newFld != null) {
                changed[0] = true;
            }
            newFld = applyNamespaceToAnonymousType(namespace, newFld != null ? newFld : fld, nestLevel + 1,
                    newFld == null);
            if (newFld != null) {
                changed[0] = true;
            }
            return newFld != null ? newFld : fld;
        }));
        if (changed[0]) {
            var newAnonData = cloneMap(anonData);
            newAnonData.put(DN_FIELDS, newFields);
            if (cloneIfChanged) {
                var newFieldData = cloneMap(fieldData);
                newFieldData.put(DN_TYPE_DEF, newAnonData);
                return newFieldData;
            } else {
                // Field data started out being cloned.
                fieldData.put(DN_TYPE_DEF, newAnonData);
                return fieldData;
            }
        }
        return null;
    }

    public static DnRawType mkType(String typeName, List<DnRawField> fields) throws DnException {
        return DnRawType.extract(mMap(DN_NAME, typeName)).addFields(fields);
    }

    public static DnRawType mkSubType(String typeName, String baseTypeName) throws DnException {
        return DnRawType.extract(mMap(DN_NAME, typeName, DN_BASE_TYPE, baseTypeName));
    }

    public DnRawType addFields(List<DnRawField> fields) {
        rawFields.addAll(fields);
        return this;
    }

    public DnRawType setOption(String optionName, Object optionValue) {
        model.put(optionName, optionValue);
        return this;
    }

    public DnRawType setOptions(Map<String,Object> options) {
        model.putAll(options);
        return this;
    }

    // Called when raw type is added to a package.
    public void finish() {
        if (rawFields.size() > 0) {
            var fields = nMapSimple(rawFields, (fld -> {
                fld.finish();
                return fld.data;
            }));
            model.put(DN_FIELDS, fields);
        }
    }

}
