package org.dynamicruntime.schemadef;

import org.dynamicruntime.exception.DnException;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.util.Comparator;
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
    public final Double min;
    public final Double max;
    public final List<DnField> fields;
    public final Map<String,DnField> fieldsByName;
    public final Map<String,Object> model;
    public final boolean isSimple;


    /** DnRawField has unmodifiable fields, this class has modifiable fields. */
    static class RawField {
        String name;
        Map<String,Object> data;
        int nestLevel;
        int sortRank;

        RawField(String name, Map<String,Object> data, int nestLevel, int sortRank) {
            this.name = name;
            this.data = data;
            this.nestLevel = nestLevel;
            this.sortRank = sortRank;
        }

        static RawField mk(Map<String,Object> data, int nestLevel) throws DnException {
            String name = getReqStr(data, DN_NAME);
            int rank = (int) getLongWithDefault(data, DN_SORT_RANK, DN_DEFAULT_SORT_RANK);
            return new RawField(name, data, nestLevel, rank);
        }
    }

    /** Holds results from recursive merging of base types. */
    static class MergeData {
        Map<String,Object> model;
        List<RawField> rawFields;
        String baseType;
    }

    public DnType(String name, String baseType, boolean noTrimming, boolean noCommas, Double min, Double max,
            List<DnField> fields, Map<String,Object> model) {
        this.name = name;
        this.baseType = baseType;
        this.noTrimming = noTrimming;
        this.noCommas = noCommas;
        this.min = min;
        this.max = max;

        // Note that we could try to use Collections.unmodifiableCollection, but the unmodifiable objects
        // are unfriendly in IntelliJ when inspecting them in the debugger, so we don't do that.
        // We do not use code to enforce the fact that the collections and maps should not be modified and
        // trust future programmers to read the above comments not do this.
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

        // Merge in base types, with a particular focus on fields.
        MergeData md = mergeWithBaseType(model, types, 0);
        var newModel = md.model;
        Double min = getOptDouble(newModel, DN_MIN);
        Double max = getOptDouble(newModel, DN_MAX);

        List<RawField> newRawFields = null;

        // Collapse raw fields, later fields map data is merged with earlier fields of the same name.
        if (md.rawFields != null) {
            // Sort raw fields (note we are really depending on this being a stable sort.
            md.rawFields.sort(Comparator.comparingInt(f -> f.sortRank));
            Map<String,RawField> foundFields = mMapT();
            newRawFields = mList();
            for (var rawField : md.rawFields) {
                RawField existing = foundFields.get(rawField.name);
                if (existing != null) {
                    // Depending on nest level, we can merge in two different ways but with
                    // lower nest level fields merging their data into higher nest level fields.
                    if (rawField.nestLevel <= existing.nestLevel) {
                        existing.data.putAll(rawField.data);
                    } else {
                        rawField.data.putAll(existing.data);
                        // Switch in the new merged data for existing.
                        existing.data = rawField.data;
                    }
                } else {
                    newRawFields.add(rawField);
                    foundFields.put(rawField.name, rawField);
                }
            }
        }

        // At this point, all cloning that needs to be done has been done.
        List<DnField> dnFields = null;
        String baseType = null;
        if (newRawFields != null) {
            dnFields = nMap(newRawFields, (rawFld ->
                    getBoolWithDefault(rawFld.data, DN_DISABLED, false) ?
                            null :
                            DnField.extract(rawFld.data, types)));
        } else {
            baseType = md.baseType;
        }

        // Remove *dnFields* from model since they have been fully extracted and after the merging
        // with base type, they may have no correspondence with the version we extracted.
        newModel.remove(DN_FIELDS);

        return new DnType(name, baseType, noTrimming, noCommas, min, max, dnFields, newModel);
    }

    public static MergeData mergeWithBaseType(Map<String,Object> model, Map<String,DnRawType> types, int nestLevel)
            throws DnException {
        if (nestLevel > 10) {
            throw new DnException("Nesting of types is recursive at type " + model.get(DN_NAME) + ".");
        }
        List<Map<String,Object>> fields = getOptListOfMaps(model, DN_FIELDS);
        List<RawField> rawFields = null;
        if (fields != null && fields.size() > 0) {
            rawFields = nMap(fields, (fld -> RawField.mk(fld, nestLevel)));
        }
        String baseTypeName = getOptStr(model, DN_BASE_TYPE);

        var md = new MergeData();
        if (baseTypeName == null || isPrimitive(baseTypeName)) {
            md.model = cloneMap(model);
            md.rawFields = rawFields;
            md.baseType = baseTypeName;
            return md;
        }

        DnRawType baseType = types.get(baseTypeName);
        if (baseType == null) {
            throw DnException.mkConv("Could not find base type " + baseTypeName +
                    " referenced by " + model.get(DN_NAME) + ".");
        }

        var mdBase = mergeWithBaseType(baseType.model, types, nestLevel + 1);
        var newModel = mdBase.model; // Note model has been cloned, safe to change.

        newModel.putAll(model);
        if (!model.containsKey(DN_NAME)) {
            // Do not inherit names, just causes confusion.
            newModel.remove(DN_NAME);
        }
        var newRawFields = rawFields != null && rawFields.size() > 0 ? rawFields : null;
        if (mdBase.rawFields != null) {
            if (rawFields != null) {
                newRawFields = mList();
                newRawFields.addAll(mdBase.rawFields);
                // New raw fields go on the end of list of fields by default. Use the *sortRank* to change this.
                newRawFields.addAll(rawFields);
            } else{
                newRawFields = mdBase.rawFields;
            }
        }

        /*
        boolean promoteBaseType = (mdBase.baseType != null && newRawFields == null);
        String newBaseTypeName = promoteBaseType ? mdBase.baseType : baseTypeName;
        if (promoteBaseType) {
            newModel.put(DN_BASE_TYPE, newBaseTypeName);
        }*/
        //String newBaseTypeName = baseTypeName;

        md.model = newModel;
        md.rawFields = newRawFields;
        md.baseType = baseTypeName;
        return md;
    }

    @Override
    public String toString() {
        String n = name;
        if (n == null && baseType != null) {
            n = ":" + baseType;
        }
        n = (n != null) ? n : "anon";
        return String.format("%s{fields=%s}", n, (fields != null) ? fields.toString() : "<no-fields>");
    }

    public Map<String,Object> toMap() {
        List<Map<String,Object>> fieldData = (fields != null) ?
                nMapSimple(fields, DnField::toMap) : null;
        if (fieldData != null) {
            Map<String,Object> retVal = cloneMap(model);
            retVal.put(DN_FIELDS, fieldData);
            return retVal;
        }
        return model;
    }
}
