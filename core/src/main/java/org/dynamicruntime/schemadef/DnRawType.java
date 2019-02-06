package org.dynamicruntime.schemadef;

import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.util.List;
import java.util.Map;

/** Holds a raw map with schema definition. Following the convention of this application, we pull out fields
 * from the map that are relevant to the operations performed using the class. This class also
 * has convenience methods designed to ease the process of creating a DnType. */
@SuppressWarnings({"WeakerAccess", "unused"})
public class DnRawType implements DnRawTypeInterface {
    public final String name;
    // This data can be mutated (following certain rules) either before it is added to a package
    // or after it has been cloned.
    public final Map<String,Object> model;
    @SuppressWarnings("unused")
    // Present for debug purposes.
    public final String namespace; // Can be null if a namespace has not been applied.

    // Temporary mutable data. Used to help construction process. If this attribute becomes
    // populated, then at the time this type is added to its package, the *dnFields* attribute
    // in the model will be replaced with this data.
    public final List<DnRawField> rawFields = mList();

    public DnRawType(String name, Map<String,Object> model) {
        this.name = name;
        this.model = model;
        this.namespace = (name != null) ? StrUtil.getBeforeLastIndex(name, ".") : null;
    }

    public static DnRawType extract(Map<String,Object> model) {
        String name = getOptStr(model, DN_NAME);
        return new DnRawType(name, model);
    }

    @Override
    public DnRawType getRawType() {
        return this;
    }

    /** Clones down to field data. Cloning is our main defense against data pollution across
     * schemas. Note that any inline types (types that are not registered) for fields get cloned,
     * but only the parts that are necessary to clone. */
    public DnRawType cloneType(String namespace) {
        Map<String,Object> newModel = cloneMap(model);

        // See if reference to base type needs to have a namespace applied to it.
        DnTypeUtils.updateTypeIfChanged(namespace, DN_BASE_TYPE, newModel, false);

        // See if references to traits need to have namespace applied to them./
        DnTypeUtils.updateTypesIfChanged(namespace, DN_TYPE_REFS_FIELDS_ONLY, newModel, false);

        var fields = getOptListOfMaps(newModel, DN_FIELDS);
        if (fields != null) {
            var newFields = nMapSimple(fields, (fld -> {
                // We always clone the top level field data. Allows us to change top level
                // field data as desired.
                var m = cloneMap(fld);

                if (namespace != null) {
                    DnTypeUtils.updateTypeIfChanged(namespace, DN_TYPE_REF, m, false);
                    DnTypeUtils.updateTypesIfChanged(namespace, DN_TYPE_REFS_FIELDS_ONLY, m, false);
                    applyNamespaceToInlineType(namespace, m, 0, false);
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

    public Map<String,Object> applyNamespaceToInlineType(String namespace, Map<String,Object> fieldData,
            int nestLevel, boolean cloneIfChanged) {
        if (nestLevel > 10) {
            throw new RuntimeException("Inline type for field " + getOptStr(fieldData, DN_NAME) +
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
        // If changed becomes true, then we need to clone.
        boolean[] changed = {false};
        var newFields = nMapSimple(fields, (fld -> {
            // We want to clone if there is a change, but once field has been cloned, it does not need to be
            // cloned again.
            var newFld = DnTypeUtils.updateTypeIfChanged(namespace, DN_TYPE_REF, fld, true);
            if (newFld != null) {
                changed[0] = true;
            }
            // If newFld is null, then we have not already cloned.
            newFld = DnTypeUtils.updateTypesIfChanged(namespace, DN_TYPE_REFS_FIELDS_ONLY,
                    newFld != null ? newFld : fld, newFld == null);
            if (newFld != null) {
                changed[0] = true;
            }
            // If newFld is null, then we have not already cloned.
            newFld = applyNamespaceToInlineType(namespace, newFld != null ? newFld : fld, nestLevel + 1,
                    newFld == null);
            if (newFld != null) {
                changed[0] = true;
            }
            return newFld != null ? newFld : fld;
        }));
        if (changed[0]) {
            var newAnonData = cloneMap(anonData);

            // See if reference to base type needs to have a namespace applied to it. We have already
            // cloned, so we do not need to clone again.
            DnTypeUtils.updateTypeIfChanged(namespace, DN_BASE_TYPE, newAnonData, false);
            DnTypeUtils.updateTypesIfChanged(namespace, DN_TYPE_REFS_FIELDS_ONLY, newAnonData, false);

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
        } else {
            var newAnonData = DnTypeUtils.updateTypeIfChanged(namespace, DN_BASE_TYPE,
                    anonData, true);
            if (newAnonData != null) {
                return newAnonData;
            }
        }
        return null;
    }

    public static DnRawType mkType(String typeName, List<DnRawField> fields) {
        return DnRawType.extract(mMap(DN_NAME, typeName)).addFields(fields);
    }

    public static DnRawType mkType(List<DnRawField> fields) {
        return new DnRawType(null, mMap()).addFields(fields);
    }

    /** Makes a type that extends from another type. If the type is inline then the *typeName*
     * can be null. */
    public static DnRawType mkSubType(String typeName, String baseTypeName) {
        return new DnRawType(typeName, mMap(DN_NAME, typeName, DN_BASE_TYPE, baseTypeName));
    }

    public static DnRawType mkSubType(String baseTypeName) {
        return new DnRawType(null, mMap(DN_BASE_TYPE, baseTypeName));
    }

    @SuppressWarnings("UnusedReturnValue")
    public DnRawType addField(DnRawField field) {
        rawFields.add(field);
        return this;
    }

    public DnRawType addFields(List<DnRawField> fields) {
        if (fields == null) {
            return this;
        }
        rawFields.addAll(fields);
        return this;
    }

    /** Adds in traits to be merged in. */
    public DnRawType setReferencedTypesWithFields(List<String> refTypesWithFields) {
        model.put(DN_TYPE_REFS_FIELDS_ONLY, refTypesWithFields);
        return this;
    }

    public DnRawType setAttribute(String optionName, Object optionValue) {
        model.put(optionName, optionValue);
        return this;
    }

    public DnRawType setAttributes(Map<String,Object> options) {
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
        if (name != null && !model.containsKey(DN_NAME)) {
            model.put(DN_NAME, name);
        }
    }
}
