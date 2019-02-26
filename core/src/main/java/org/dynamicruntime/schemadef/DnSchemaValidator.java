package org.dynamicruntime.schemadef;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.ParsingUtil;
import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;


import java.util.*;

@SuppressWarnings("WeakerAccess")
public class DnSchemaValidator {
    /** Request mode gets the strongest validation because it is the one place we can stop bad data from coming
     * into the system. */
    public static final String REQUEST_MODE = "request";
    @SuppressWarnings("unused")
    public static final String STORAGE_MODE = "storage";
    @SuppressWarnings("unused")
    public static final String RESPONSE_MODE = "response";
    public final DnCxt cxt;
    public final String mode;
    public final DnSchemaStore schemaStore;

    public DnSchemaValidator(DnCxt cxt, String mode) {
        this.cxt = cxt;
        this.mode = mode;
        this.schemaStore = cxt.getSchema();
    }

    public Map<String,Object> validateAndCoerce(DnType type, Map<String,Object> data) throws DnException {
        var result = validateAndCoerceImpl(type, data, 0);
        return result != null ? result : mMap();
    }

    public Map<String,Object> validateAndCoerceImpl(DnType type, Map<String,Object> data, int nestLevel) throws DnException {
        if (nestLevel > 10) {
            throw DnException.mkConv("Nested data structure for supplied data is too deep.");
        }
        Set<String> unconsumedKeys = new HashSet<>(data.keySet());
        Map<String,Object> output = mMap();
        if (type.isSimple) {
            if (type.baseType == null || !type.baseType.equals(DNT_NONE)) {
                // Bad logic in code, not problem with data being supplied.
                throw new DnException("Validating a Map with the simple type.");
            }
        } else {
            for (DnField field: type.fields) {
                Object obj = data.get(field.name);
                String fieldRefType = field.typeRef;
                Object newObj;
                if (fieldRefType != null && isPrimitive(fieldRefType)) {
                    newObj = validateAndCoercePrimitive(field, null, fieldRefType, field.isList, obj);
                } else {
                    DnType fieldType = getType(field);
                    newObj = validateAndCoerce(field, fieldType, obj, nestLevel);
                }
                if (newObj != null) {
                    output.put(field.name, newObj);
                }
                unconsumedKeys.remove(field.name);
            }
        }
        if (!unconsumedKeys.isEmpty() && mode.equals(REQUEST_MODE)) {
            throw DnException.mkConv(String.format("Extra fields %s were supplied that are not referenced in " +
                    "schema.", fmtObject(unconsumedKeys)));
        }
        return output.isEmpty() ? null : output;
    }

    public DnType getType(DnField field) throws DnException {
        DnType type = (field.anonType != null) ? field.anonType : getType(field.typeRef);
        if (type == null) {
            // Infrastructure problem.
            throw new DnException("Type " + field.typeRef + " specified by field " + field.name + " has not " +
                    "been defined.");
        }
        return type;
    }

    public DnType getType(String typeName) {
        return schemaStore.types.get(typeName);
    }


    public Object validateAndCoerce(DnField field, DnType type, Object obj, int nestLevel) throws DnException {
        boolean isSimple = type.isSimple;
        Object newObj;
        if (isSimple) {
            newObj = validateAndCoercePrimitive(field, type, field.coreType, field.isList, obj);
        } else {
            newObj = validateAndCoerceComplex(field, type, obj, field.isList, nestLevel);
        }
        return newObj;
    }

    public Object validateAndCoerceComplex(DnField field, DnType type, Object obj, boolean isList, int nestLevel)
        throws DnException {
        Object cObj = obj;
        Object newObj = null;
        if (obj instanceof CharSequence) {
            String s = toTrimmedOptStr(obj);
            if (s != null) {
                cObj = isList ?
                        ParsingUtil.toJsonMap(s) :
                        ParsingUtil.toJsonList(s);
            } else {
                cObj = isList ? mList() : mMap();
            }
        }
        if (isList) {
            // A list parse always returns a non-null list. Currently we do not allow schema to say
            // that list has to be non-empty.
            if (cObj == null) {
                cObj = mList();
            }
            if (!(cObj instanceof List)) {
                throw DnException.mkConv(String.format("Field value is not a list for field %s.",
                        field.name));
            }
            List<?> l = (List<?>)cObj;
            List<Object> outValues = mList();
            for (Object o : l) {
                var no = validateAndCoerceComplex(field, type, o, false, nestLevel);
                outValues.add(no);
            }
            if (field.isRequired && outValues.isEmpty()) {
                throw DnException.mkConv(String.format("Required field %s has an empty list.", field.name));
            }
            newObj = outValues;
        } else {
            Map<String,Object> map = toOptMap(cObj);
            if (obj != null && map == null) {
                throw DnException.mkConv(String.format("Could not coerce value for field %s " +
                        "to a Map.", field.name));
            }
            if (map != null) {
                try {
                    newObj = validateAndCoerceImpl(type, map, nestLevel + 1);
                } catch (DnException e)  {
                    throw DnException.mkConv(String.format("Could not validate complex field %s.", field.name), e);
                }
            }
            if ((field.isList || field.isRequired) && newObj == null) {
                String msg = (field.isList) ?
                        String.format("List element for field %s was null or empty.", field.name) :
                        String.format("Required field %s was not supplied a value.", field.name);
                throw DnException.mkConv(msg);
            }
        }
        return newObj;
    }

    /** The core part of the validation. Note that the *type* parameter can definitely be null. */
    public Object validateAndCoercePrimitive(DnField field, DnType type, String coreType,
            boolean isList, Object obj) throws DnException {
        String fieldName = (field != null) ? field.name : "root";
        if (obj == null && field != null) {
            obj = field.data.get(DN_DEFAULT_VALUE);
        }
        boolean isNoCommas = type != null && type.noCommas;
        boolean isNoTrim = type != null && type.noTrimming;
        boolean checkIsSmall = (coreType.equals(DNT_STRING) && type == null)  || (type != null && !type.isLarge);
        Object out = null;

        if (isList) {
            List<Object> l;
            if (obj instanceof Collection) {
                var c = (Collection)obj;
                l = mList();
                for (Object o : c) {
                    l.add(validateAndCoercePrimitive(field, type, coreType, false, o));
                }
            } else if (obj instanceof CharSequence) {
                // Still have a chance to parse this.
                if (checkIsSmall) {
                    CharSequence cs = (CharSequence)obj;
                    if (cs.length() > 255) {
                        throw DnException.mkConv(String.format("The multi-value for the field %s has size %d " +
                                "which exceeds the maximum for a small field of 255.", fieldName, cs.length()));
                    }
                }
                if ((coreType.equals(DNT_STRING) && isNoCommas) || coreType.equals(DNT_BOOLEAN) ||
                        coreType.equals(DNT_INTEGER) || coreType.equals(DNT_FLOAT) || coreType.equals(DNT_DATE)) {
                    var c = StrUtil.splitString(obj.toString(), ",");
                    l = mList();
                    for (Object o : c) {
                        var outObj = validateAndCoercePrimitive(field, type, coreType,false, o);
                        if (outObj == null && coreType.equals(DNT_STRING)) {
                            // Do not allow nulls in lists of strings.
                            outObj = "";
                        }
                        l.add(outObj);
                    }
                } else {
                    throw DnException.mkConv(String.format("Cannot convert a string into a list of objects of type " +
                            "%s for field %s.", coreType, fieldName));
                }

            } else if (obj == null) {
               l = mList();
            } else {
                throw DnException.mkConv(String.format("Object is not a collection type for field %s.", fieldName));
            }
            if (l.isEmpty() && (field != null && field.isRequired)) {
                throw DnException.mkConv(String.format("Required field %s has an empty list.", fieldName));
            }
            out = l;
        }  else {
            boolean isNumber = false;
            try {
                if (obj != null) {
                    switch (coreType) {
                        case DNT_STRING:
                            String s = isNoTrim ? toOptStr(obj) : toTrimmedOptStr(obj);
                            if (checkIsSmall && s != null) {
                                if (s.length() > 255) {
                                    throw DnException.mkConv(String.format(
                                            "The multi-value for the field %s has size %d " +
                                            "which exceeds the maximum for a small field of 255.",
                                            fieldName, s.length()));
                                }
                            }

                            if (isNoCommas && s != null && s.indexOf(',') >= 0) {
                                throw DnException.mkConv(String.format(
                                        "String '%s' is not allowed to have commas for field %s.", s, fieldName));
                            }
                            out = s;
                            break;
                        case DNT_BOOLEAN:
                            out = toOptBool(obj);
                            break;
                        case DNT_INTEGER:
                            out = toOptLong(obj);
                            isNumber = true;
                            break;
                        case DNT_FLOAT:
                            out = toOptDouble(obj);
                            isNumber = true;
                            break;
                        case DNT_DATE:
                            out = toOptDate(obj);
                            break;
                        case DNT_MAP:
                            if (obj instanceof CharSequence) {
                                out = ParsingUtil.toJsonMap(obj.toString());
                            } else {
                                out = toOptMap(obj);
                            }
                            break;
                        default:
                            // Just let the object float through.
                            out = obj;
                            break;
                    }
                }
            } catch (DnException e) {
                throw DnException.mkConv(String.format("Cannot coerce field '%s' into type %s.",
                        fieldName, coreType), e);
            }

            if (field != null && (field.isRequired || field.isList) && out == null) {
                String msg;
                if (field.isList) {
                    msg = String.format("List element was null or empty for field %s.", fieldName);
                } else if (objIsEmpty(obj)) {
                    msg = String.format("Required field %s was not supplied a value.", fieldName);
                } else {
                    msg = String.format("Required field %s was not supplied a valid value.", fieldName);
                }
                throw DnException.mkConv(msg);
            }


            // Eventually may put in custom validators that are registered with the schema engine.

            if (out != null && isNumber && type != null && (type.min != null || type.max != null)) {
                Double d = toOptDouble(out);
                if (d != null) {
                    if (type.min != null && d < type.min - 0.0001) {
                        throw DnException.mkConv(String.format("Value %s is below minimum %s for field %s.",
                                fmtTrimmedDouble(d), fmtTrimmedDouble(type.min), fieldName));
                    }
                    if (type.max != null && d >= type.max - 0.0001) {
                        throw DnException.mkConv(String.format("Value %s is not below maximum %s for field %s.",
                                fmtTrimmedDouble(d),  fmtTrimmedDouble(type.max), fieldName));
                    }
                }
            }
        }
        if (obj != null && out == null) {
            throw DnException.mkConv(String.format("Could not convert object '%s' " +
                    "using the type %s for field '%s'.", fmtObject(obj), coreType, fieldName));
        }
        return out;
    }

    public static boolean objIsEmpty(Object obj) {
        if (obj == null) {
            return true;
        } else if (obj instanceof CharSequence) {
            String s = obj.toString().trim();
            return s.isEmpty();
        }
        return false;
    }
}
