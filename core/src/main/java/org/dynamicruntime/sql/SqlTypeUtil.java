package org.dynamicruntime.sql;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnField;
import org.dynamicruntime.schemadef.DnType;
import org.dynamicruntime.util.ParsingUtil;
import org.dynamicruntime.util.StrUtil;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

@SuppressWarnings("WeakerAccess")
public class SqlTypeUtil {
    /** We add additional mappings as we need them. For now we have postgres bias. */
    public static String toDnType(int sqlType) {
        switch (sqlType) {
            case Types.VARCHAR:
            case Types.CLOB:
                return DNT_STRING;
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.SMALLINT:
            case Types.TINYINT:
                return DNT_INTEGER;
            case Types.BINARY:
            case Types.BLOB:
                return DNT_BINARY;
            case Types.BOOLEAN:
            case Types.BIT:
                return DNT_BOOLEAN;
            case Types.DECIMAL:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL:
                return DNT_FLOAT;
            case Types.TIMESTAMP:
            case Types.DATE:
            case Types.TIME:
                return DNT_DATE;
            default:
                return DNT_NONE;
        }
    }

    public static String toDbType(SqlCxt sqlCxt, DnField field) {
        // With postgres and H2, this mapping is straightforward, but not so with other databases (which we will
        // support at a later time, maybe).
        if (field.isList) {
            // Lists get encoded as strings.
            return "varchar";
        }

        String coreType = field.coreType;
        switch (coreType) {
            case DNT_STRING:
                // H2 and Postgres support using a *varchar* without a maximum length without any
                // performance implications.
                return "varchar";
            case DNT_INTEGER:
                // Always use 8-bits, costs little for a bit of peace of mind.
                boolean isAutoNumber = field.isAutoIncrementing();
                if (isAutoNumber) {
                    if (sqlCxt.sqlDb.options.hasSerialType) {
                        return "bigserial";
                    }
                    return "bigint auto_increment";
                }
                return "bigint";
            case DNT_FLOAT:
                // We do the simple thing here. Not clear we need more precision.
                return "float";
            case DNT_DATE:
                if (sqlCxt.sqlDb.options.useTimezoneWithTz) {
                    // Postgresql custom type that can be useful.
                    return "timezonetz";
                }
                return "timestamp";
            case DNT_BOOLEAN:
                return "boolean";
            case DNT_BINARY:
                return "blob";
            case DNT_MAP:
            case DNT_GENERIC:
                return "varchar";
         }
         return "varchar";
    }

    /** Converts and binds a parameter to a field in a prepared statement. */
    public static void setStmtParameter(DnCxt cxt, int index, String stmtName, PreparedStatement pStmt,
            DnField fld, Object obj) throws DnException {
        try {
            String coreType = fld.coreType;
            boolean isList = fld.isList;
            if (isList) {
                String str;
                if (obj == null) {
                    str= null;
                }
                else if (obj instanceof List) {
                    List<?> l = (List<?>) obj;
                    // Needs special handling.
                    boolean entitiesHaveCommas = entitiesCanHaveCommas(cxt, fld);
                    if (entitiesHaveCommas) {
                        str = ParsingUtil.toJsonString(obj);
                    } else {
                        // If the *noCommas* is a lie, then we can get into trouble.
                        var lStr = nMapSimple(l, (e -> (e != null) ? fmtObject(e) : ""));
                        str = String.join(",", lStr);
                    }
                } else if (obj instanceof CharSequence) {
                    str = obj.toString();
                } else {
                    throw new DnException("Supplied object was not a list.");
                }
                pStmt.setString(index, str);
            } else {
                switch (coreType) {
                    case DNT_STRING:
                    case DNT_MAP:
                    case DNT_GENERIC: // Generic always resolves to some type of map.
                        String s;
                        if (obj == null) {
                            s = null;
                        }
                        else if (coreType.equals(DNT_STRING)) {
                            s = toOptStr(obj);
                        } else {
                            if (obj instanceof CharSequence) {
                                s = obj.toString();
                            } else if (obj instanceof Map) {
                                s = ParsingUtil.toJsonString(obj);
                            } else {
                                throw new DnException("Supplied object is not a Map.");
                            }
                        }
                        pStmt.setString(index, s);
                        break;
                    case DNT_BOOLEAN:
                        Boolean b = toOptBool(obj);
                        if (b != null) {
                            pStmt.setBoolean(index, b);
                        } else {
                            pStmt.setNull(index, Types.BOOLEAN);
                        }
                        break;
                    case DNT_DATE:
                        Date d = toOptDate(obj);
                        if (d != null) {
                            pStmt.setTimestamp(index, new java.sql.Timestamp(d.getTime()));
                        } else {
                            pStmt.setTimestamp(index, null);
                        }
                        break;
                    case DNT_FLOAT:
                        Double db = toOptDouble(obj);
                        if (db != null) {
                            pStmt.setFloat(index, db.floatValue());
                        } else {
                            pStmt.setNull(index, Types.FLOAT);
                        }
                        break;
                    case DNT_INTEGER:
                        Long l = toOptLong(obj);
                        if (l != null) {
                            pStmt.setLong(index, l);
                        } else {
                            pStmt.setNull(index, Types.BIGINT);
                        }
                        break;
                    default:
                        // Eventually we will do something for binary.
                        throw new DnException("Unsupported type " + coreType + " for storing into database.");
                }
            }

        } catch (Exception e) {
            throw SqlStmtUtil.mkDnException(String.format("Could not set parameter %s with value %s for " +
                    "statement %s.", fmtLog(obj), fld.name, stmtName), e);
        }
    }

    public static Object convertDbObject(DnCxt cxt, DnField fld, Object obj) {
        return convertDbObject(cxt, fld, obj, false);
    }

    /** Uses the schema definition to help in decoding the value stored in a database into the value
     * used in code. */
    public static Object convertDbObject(DnCxt cxt, DnField fld, Object obj, boolean insideList) {
        if (obj == null) {
            return null;
        }
        // First see if entry is a list that needs to be parsed.
        if (fld.isList && !insideList) {
            String s = toOptStr(obj);
            if (s == null || s.trim().isEmpty()) {
                return null;
            }
            boolean entitiesHaveCommas = entitiesCanHaveCommas(cxt, fld);
            List<Object> objList;
            if (entitiesHaveCommas) {
                try {
                    objList = ParsingUtil.toJsonList(s);
                } catch (DnException e) {
                    LogSql.log.error(cxt, e, "Suppressing failed conversion of " + s + " into a list.");
                    return null;
                }
            } else {
                List<String> items = StrUtil.splitString(s, ",");
                objList = mList();
                for (var item : items) {
                    item = item.trim();
                    if (item.isEmpty()) {
                        objList.add(null);
                    } else {
                        Object o = convertDbObject(cxt, fld, item, true);
                        objList.add(o);
                    }
                }
            }
            return objList;
        }

        // Now in simpler case.
        Object retVal = null;
        switch (fld.coreType) {
            case DNT_BOOLEAN:
                retVal = toOptBool(obj);
                break;
            case DNT_INTEGER:
                try {
                    retVal = toOptLong(obj);
                } catch (Exception ignore) { }
                break;
            case DNT_FLOAT:
                try {
                    retVal = toOptDouble(obj);
                } catch (Exception ignore) { }
                break;
            case DNT_DATE:
                try {
                    retVal = toOptDate(obj);
                    if (retVal instanceof Timestamp) {
                        retVal = new Date(((Timestamp)retVal).getTime());
                    }
                } catch (Exception ignore) { }
                break;
            default:
                String s = obj.toString().trim();
                if (fld.coreType.equals(DNT_MAP) || fld.coreType.equals(DNT_GENERIC)) {
                    if (s.startsWith("{")) {
                        try {
                            retVal = ParsingUtil.toJsonMap(s);
                        } catch (DnException e) {
                            LogSql.log.error(cxt, e, "Suppressing failed conversion of " + s +
                                    " into a map.");
                        }
                    }
                } else {
                    retVal = s.isEmpty() ? null : s;
                }
        }
        return retVal;
    }

    /** Determines, based on schema, whether an individual entity in a list of entities could
     * have a comma. */
    public static boolean entitiesCanHaveCommas(DnCxt cxt, DnField fld) {
        boolean entitiesHaveCommas = true;
        switch (fld.coreType) {
            case DNT_BOOLEAN:
            case DNT_INTEGER:
            case DNT_FLOAT:
            case DNT_DATE:
                return false;
            case DNT_MAP:
            case DNT_GENERIC:
                return true;
        }

        if ((fld.anonType != null || (fld.typeRef != null && !fld.typeRef.equals(fld.coreType)))) {
            DnType type = (fld.anonType != null) ? fld.anonType :
                    cxt.getSchema().getType(fld.typeRef);
            if (type != null && type.noCommas) {
                entitiesHaveCommas = false;
            }
        }
        return entitiesHaveCommas;
    }

}
