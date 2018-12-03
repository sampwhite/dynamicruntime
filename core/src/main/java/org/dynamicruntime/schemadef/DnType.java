package org.dynamicruntime.schemadef;

import org.dynamicruntime.exception.DnException;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.util.List;
import java.util.Map;

/** This is a read only export of the definition of a schema type optimized for consumption. Anything
 * accessed from here should *not* be modified. */
@SuppressWarnings("WeakerAccess")
public class DnType {
    public final String name;
    public final String baseType;
    public final boolean noTrimming;
    public final boolean noCommas;
    public final Long min;
    public final Long max;
    public final List<DnField> fields;
    public final Map<String,DnField> fieldsByName;
    public final Map<String,Object> model;
    public final boolean isSimple;

    /** Holds results from recursive merging of base types. */
    static class MergeData {
        Map<String,Object> model;
        List<DnRawField> rawFields;
        String baseType;
    }

    /** DnRawField has unmodifiable fields, this class has modifiable fields. */
    static class RawField {
        String name;
        Map<String,Object> data;
        RawField(String name, Map<String,Object> data) {
            this.name = name;
            this.data = data;
        }
    }

    public DnType(String name, String baseType, boolean noTrimming, boolean noCommas, Long min, Long max,
            List<DnField> fields, Map<String,Object> model) {
        this.name = name;
        this.baseType = baseType;
        this.noTrimming = noTrimming;
        this.noCommas = noCommas;
        this.min = min;
        this.max = max;

        // Note that we could try to use Collections.unmodifiableCollection, but the unmodifiable objects
        // are unfriendly in IntelliJ when inspecting them in the debugger, so we don't do that.
        // So we do not use code to enforce the fact that the collections and maps should not be modified, we will
        // trust future programmers to read the comments and not do this.
        this.fields = fields;
        this.model = model;
        this.isSimple = this.fields == null;
        if (!this.isSimple) {
            Map<String,DnField> fldByName = mMapT();
            for (DnField fld : fields) {
                fldByName.put(fld.name, fld);
            }
            this.fieldsByName = fldByName;
        } else {
            this.fieldsByName = null;
        }
    }

    public static DnType extractAnon(Map<String,Object> model, Map<String,DnRawType> types) throws DnException {
        String name = getOptStr(model, DN_NAME);
        return extractNamed(name, model, types);
    }

    public static DnType extract(Map<String,Object> model, Map<String,DnRawType> types) throws DnException {
        String name = getReqStr(model, DN_NAME);
        return extractNamed(name, model, types);
    }

    public static DnType extractNamed(String name, Map<String,Object> model, Map<String,DnRawType> types) throws DnException {
        boolean noTrimming = getBoolWithDefault(model, DN_NO_TRIMMING, false);
        boolean noCommas = getBoolWithDefault(model, DN_NO_COMMAS, false);
        Long min = getOptLong(model, DN_MIN);
        Long max = getOptLong(model, DN_MAX);

        // Merge in base types, with a particular focus on fields.
        MergeData md = mergeWithBaseType(model, types, 0);

        List<RawField> newRawFields = null;

        // Collapse raw fields, later fields map data is merged with earlier fields of the same name.
        if (md.rawFields != null) {
            Map<String,RawField> foundFields = mMapT();
            newRawFields = mList();
            for (var rawField : md.rawFields) {
                var newRawField = new RawField(rawField.name, cloneMap(rawField.data));
                RawField existing = foundFields.get(rawField.name);
                if (existing != null) {
                    // New data is merged into existing.
                    existing.data.putAll(newRawField.data);
                } else {
                    newRawFields.add(newRawField);
                    foundFields.put(newRawField.name, newRawField);
                }
            }
        }

        // At this point, all cloning that needs to be done has been done.
        List<DnField> dnFields = null;
        String baseType = null;
        if (newRawFields != null) {
            dnFields = nMap(newRawFields, (fld -> DnField.extract(fld.data, types)));
        } else {
            baseType = md.baseType;
        }

        // Remove *dnFields* from model since they have been fully extracted and after the merging
        // with base type, they may have no correspondence with the version we extracted.
        model.remove(DN_FIELDS);

        return new DnType(name, baseType, noTrimming, noCommas, min, max, dnFields, model);
    }

    public static MergeData mergeWithBaseType(Map<String,Object> model, Map<String,DnRawType> types, int nestLevel)
            throws DnException {
        if (nestLevel > 10) {
            throw new DnException("Nesting of types is recursive at type " + model.get(DN_NAME) + ".");
        }
        List<Map<String,Object>> fields = getOptListOfMaps(model, DN_FIELDS);
        List<DnRawField> rawFields = null;
        if (fields != null && fields.size() > 0) {
            rawFields = nMap(fields, DnRawField::mkRawField);
        }

        var md = new MergeData();
        String baseTypeName = getOptStr(model, DN_BASE_TYPE);
        if (baseTypeName == null || isPrimitive(baseTypeName)) {
            md.model = cloneMap(model);
            md.rawFields = rawFields;
            md.baseType = baseTypeName;
            return md;
        }

        DnRawType baseType = types.get(baseTypeName);
        if (baseType == null) {
            throw DnException.mkConv("Could not find base type " + baseTypeName +
                    " referenced by " + model.get(DN_NAME) + ".", null);
        }

        var mdBase = mergeWithBaseType(baseType.model, types, nestLevel + 1);
        var newModel = mdBase.model; // Note model has been cloned, safe to change.

        newModel.putAll(model);
        var newRawFields = rawFields != null && rawFields.size() > 0 ? rawFields : null;
        if (mdBase.rawFields != null) {
            if (rawFields != null) {
                newRawFields = mList();
                newRawFields.addAll(mdBase.rawFields);
                newRawFields.addAll(rawFields);
            } else{
                newRawFields = mdBase.rawFields;
            }
        }

        boolean promoteBaseType = (mdBase.baseType != null && newRawFields == null);
        String newBaseTypeName = promoteBaseType ? mdBase.baseType : baseTypeName;
        if (promoteBaseType) {
            newModel.put(DN_BASE_TYPE, newBaseTypeName);
        }

        md.model = newModel;
        md.rawFields = newRawFields;
        md.baseType = newBaseTypeName;
        return md;
    }

    @Override
    public String toString() {
        String n = name;
        if (n == null && baseType != null) {
            n = ":" + baseType;
        }
        n = (n != null) ? n : "anon";
        return String.format("%s[fields=%s]", n, (fields != null) ? fields.toString() : "<no-fields>");
    }
}
