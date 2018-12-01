package org.dynamicruntime.schemadef;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.StrUtil;

import java.util.List;
import java.util.Map;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

/** This is a read only version designed for optimal consumption. Anything
 * accessed from here should *not* be modified.*/
@SuppressWarnings("WeakerAccess")
public class DnField {
    public final String name;
    public final String label;
    public final String description;
    public final String typeRef;
    public final DnType anonType;
    public final boolean isList;
    public final boolean isChoice;
    public final List<DnChoice> choices;
    public final boolean isStrict;
    public final Map<String,Object> data;

    public DnField(String name, String label, String description, String typeRef, DnType anonType,
        boolean isList, boolean isChoice, List<DnChoice> choices, boolean isStrict, Map<String,Object> data) {
        this.name = name;
        this.label = label;
        this.description = description;
        this.typeRef = typeRef;
        this.anonType = anonType;
        this.isList = isList;
        this.isChoice = isChoice;
        this.choices = choices;
        this.isStrict = isStrict;
        this.data = data;
    }

    /** Creates a DnField from data in field. This method assumes the *data* object has already been cloned. */
    public static DnField extract(Map<String,Object> data, Map<String,DnRawType> types) throws DnException {
        String name = getReqStr(data, DN_NAME);
        String label = getOptStr(data, DN_LABEL);
        label = (label == null) ? StrUtil.capitalize(name) : label;
        String description = getOptStr(data, DN_DESCRIPTION);
        description = (description == null) ? "Description of " + name : description;
        String typeRef = getOptStr(data, DN_TYPE_REF);
        Map<String,Object> anonType = getOptMap(data, DN_TYPE_DEF);

        boolean isList = getBoolWithDefault(data, DN_IS_LIST, false);
        boolean isChoice = getBoolWithDefault(data, DN_TYPE_IS_CHOICES, false);
        boolean isStrict = getBoolWithDefault(data, DN_IS_STRICT, true);
        List<DnChoice> choices = null;
        if (isChoice) {
            Map<String,Object> typeData = anonType;
            if (typeData == null) {
                var dnType = typeRef != null ? types.get(typeRef) : null;
                if (dnType != null) {
                    typeData = dnType.model;
                }
            }
            var choiceFields = getListOfMapsDefaultEmpty(typeData, DN_FIELDS);
            choices = nMap(choiceFields, (cFld -> {
                String n = getReqStr(cFld, DN_NAME);
                String l = getOptStr(cFld, DN_LABEL);
                String d = getOptStr(cFld, DN_DESCRIPTION);
                l = (l == null) ? StrUtil.capitalize(n) : l;
                d = (d == null) ? "Description of " + n : d;
                Object choice = cFld.get(DN_CHOICE_VALUE);
                String strChoice = (choice != null) ? fmtObject(choice) : n;
                return new DnChoice(strChoice, l, d, cFld);
            }));
            typeRef = getOptStr(data, DN_CHOICE_TYPE_REF);
            anonType = null;
        }
        if (anonType == null && typeRef == null) {
            typeRef = DN_STRING;
        }
        DnType dnAnonType = (anonType != null) ? DnType.extract(anonType, types) : null;
        return new DnField(name, label, description, typeRef, dnAnonType, isList, isChoice, choices, isStrict,
                data);

    }

    public String toString() {
        String type = (typeRef != null) ? typeRef : "anonType";
        return String.format("%s[type=%s]", name, type);
    }
}
