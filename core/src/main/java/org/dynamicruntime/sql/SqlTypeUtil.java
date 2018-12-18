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
                return DN_STRING;
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.SMALLINT:
            case Types.TINYINT:
                return DN_INTEGER;
            case Types.BINARY:
            case Types.BLOB:
                return DN_BINARY;
            case Types.BOOLEAN:
            case Types.BIT:
                return DN_BOOLEAN;
            case Types.DECIMAL:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL:
                return DN_FLOAT;
            case Types.TIMESTAMP:
            case Types.DATE:
            case Types.TIME:
                return DN_DATE;
            default:
                return DN_NONE;
        }
    }

    public static String toDbType(SqlCxt sqlCxt, DnField field) {
        // With postgres and H2, this mapping is straightforward, but not so with other databases (which we will
        // support at a later time, maybe).
        if (field.isList) {
            // Lists get encoded as strings.
            return DN_STRING;
        }

        String coreType = field.coreType;
        switch (coreType) {
            case DN_STRING:
                // H2 and Postgres support using a *varchar* without a maximum length without any
                // performance implications.
                return "varchar";
            case DN_INTEGER:
                // Always use 8-bits, costs little for a bit of peace of mind.
                boolean isAutoNumber = getBoolWithDefault(field.data, DN_IS_AUTO_INCREMENTING, false);
                if (isAutoNumber) {
                    if (sqlCxt.sqlDb.options.hasSerialType) {
                        return "bigserial";
                    }
                    return "bigint auto_increment";
                }
                return "bigint";
            case DN_FLOAT:
                // We do the simple thing here. Not clear we need more precision.
                return "float";
            case DN_DATE:
                if (sqlCxt.sqlDb.options.useTimezoneWithTz) {
                    // Postgresql custom type that can be useful.
                    return "timezonetz";
                }
                return "timestamp";
            case DN_BOOLEAN:
                return "boolean";
            case DN_BINARY:
                return "blob";
            case DN_MAP:
            case DN_GENERIC:
                return "varchar";
         }
         return "varchar";
    }

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
                    case DN_STRING:
                    case DN_MAP:
                    case DN_GENERIC: // Generic always resolves to some type of map.
                        String s;
                        if (obj == null) {
                            s = null;
                        }
                        else if (coreType.equals(DN_STRING)) {
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
                    case DN_BOOLEAN:
                        Boolean b = toOptBool(obj);
                        if (b != null) {
                            pStmt.setBoolean(index, b);
                        } else {
                            pStmt.setNull(index, Types.BOOLEAN);
                        }
                        break;
                    case DN_DATE:
                        Date d = toOptDate(obj);
                        if (d != null) {
                            pStmt.setTimestamp(index, new java.sql.Timestamp(d.getTime()));
                        } else {
                            pStmt.setTimestamp(index, null);
                        }
                        break;
                    case DN_FLOAT:
                        Double db = toOptDouble(obj);
                        if (db != null) {
                            pStmt.setFloat(index, db.floatValue());
                        } else {
                            pStmt.setNull(index, Types.FLOAT);
                        }
                        break;
                    case DN_INTEGER:
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
            case DN_BOOLEAN:
                retVal = toOptBool(obj);
                break;
            case DN_INTEGER:
                try {
                    retVal = toOptLong(obj);
                } catch (Exception ignore) { }
                break;
            case DN_FLOAT:
                try {
                    retVal = toOptDouble(obj);
                } catch (Exception ignore) { }
                break;
            case DN_DATE:
                try {
                    retVal = toOptDate(obj);
                    if (retVal instanceof Timestamp) {
                        retVal = new Date(((Timestamp)retVal).getTime());
                    }
                } catch (Exception ignore) { }
                break;
            default:
                String s = obj.toString().trim();
                if (fld.coreType.equals(DN_MAP) || fld.coreType.equals(DN_GENERIC)) {
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

    public static boolean entitiesCanHaveCommas(DnCxt cxt, DnField fld) {
        boolean entitiesHaveCommas = true;
        switch (fld.coreType) {
            case DN_BOOLEAN:
            case DN_INTEGER:
            case DN_FLOAT:
            case DN_DATE:
                return false;
            case DN_MAP:
            case DN_GENERIC:
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
