package org.dynamicruntime.sql;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnField;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.sql.SQLTransientException;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class SqlStmtUtil {
    public static String createColumnList(SqlCxt sqlCxt, List<String> fieldDeclarations) {
        SqlColumnAliases aliases = sqlCxt.sqlDb.getAliases(sqlCxt.topic);
        List<String> colNames = nMapSimple(fieldDeclarations, (f -> aliasComposite(aliases, f)));
        return String.join(", ", colNames);
    }

    public static String aliasComposite(SqlColumnAliases aliases, String str) {
         String s = str.trim();
         int index = s.indexOf(' ');
         if (index < 0) {
             return aliases.getColumnName(s);
         }
         return aliases.getColumnName(s.substring(0, index)) + s.substring(index);
    }

    public static DnField getDnField(DnSqlStatement dnSqlStmt, SqlColumnAliases aliases, String fldName) {
        DnField fld = dnSqlStmt.fields.get(fldName);
        if (fld == null) {
            fld = aliases.reservedFields.get(fldName);
        }
        if (fld == null) {
            // If still null at this point, then we assume the type to be a string.
            fld = DnField.mkSimple(fldName, DNT_STRING);
        }
        return fld;
    }

    /**
     * Parses the query for convertible elements using an old school parsing algorithm written by an old school
     * programmer.
     *
     * -----
     * Small break for Java advertisement. This implementation is blazing fast and this is where
     * Java is an optimal fit. Languages such as C/C++ cannot do this type of coding without creating
     * security holes (assuming your programmers are not gods) and almost all other languages cannot create code
     * that executes anywhere near as fast.
     *
     * Go and Rust can maybe be this fast and do it safely, but they do not have access to the large Maven library
     * or full virtual machine capabilities making them a poor fit for large team enterprise software creation.
     * -----
     *
     * Word elements starting with a ':' are parameters. Word elements starting with
     * a 't:' are table definition names that need to be turned into actual database table names.
     * Word elements starting with 'c:' are fields that need to be mapped to column names.
     *
     * Ex:
     * Translates
     *   select * from t:myTable where c:myField = :myValue
     * to
     *   select * from my_table where my_field = ?;
     *
     * with DnSqlStatement::bindFields = new String[]{"myValue"}
     * and with the DnSqlStatement::fields having a definition of the Java type for *myValue*.
     */
    public static DnSqlStatement prepareSql(SqlCxt sqlCxt, String name, List<DnField> fields, String query) {
        SqlColumnAliases aliases = sqlCxt.sqlDb.getAliases(sqlCxt.topic);
        StringBuilder sb = new StringBuilder(query.length() + 20);
        List<String> bindFields = mList();

        // Parse current query and turn it into one we will use as a prepared statement.
        boolean inWord = false;
        boolean inQuote = false;
        int startWordIndex = -1;
        query = query.trim();
        int len = query.length();
        int curIndex = 0;
        for (int i = 0; i <= len; i++) {
            char ch = (i < len) ? query.charAt(i) : 0; // Trick to deal with last word at end of string.
            boolean allowedWordStart = ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
                    ch == '_' || ch == ':');
            if (!inWord && !inQuote) {
                // Support simple ASCII Java identifiers as words (with support for colons as well).
                if (allowedWordStart) {
                    // Use StringBuilder's ability to avoid creation of intermediate substrings.
                    sb.append(query, curIndex, i);
                    inWord = true;
                    startWordIndex = i;
                } else  if (ch == '\'') {
                    inQuote = true;
                }
            } else if (inWord) {
                boolean isLegalWordChar = allowedWordStart || (ch >= '0' && ch <= '9');
                if (!isLegalWordChar) {
                    // Ending word. We translate.
                    char ch1 = query.charAt(startWordIndex);
                    char ch2 = query.charAt(startWordIndex + 1);
                    int wordLen = i - startWordIndex;
                    if ((ch1 == ':' && wordLen > 1) || (ch2 == ':' && wordLen > 2)) {
                        // Cannot avoid it, we create a substring creating a new Java object - a slight performance
                        // hit.
                        String word = query.substring(startWordIndex, i);
                        if (ch1 == ':') {
                            // A parameter.
                            bindFields.add(word.substring(1));
                            sb.append('?');
                        } else if (ch1 == 'c') {
                            // A field turned into a column.
                            String field = word.substring(2);
                            String col = aliases.getColumnName(field);
                            sb.append(col);
                        } else if (ch1 == 't') {
                            // A table name.
                            String tableDefName = word.substring(2);
                            String tbName = sqlCxt.sqlDb.mkSqlTableName(sqlCxt, tableDefName);
                            sb.append(tbName);
                        } else {
                            // A strange expression which is likely to cause the final query to fail to execute.
                            sb.append(word);
                        }
                    } else {
                        sb.append(query, startWordIndex, i);
                    }
                    inWord = false;
                    curIndex = i;
                }
            } else { // Must have *inQuote=true*.
                if (ch == '\'') {
                    if (i < len - 1)  {
                        char nextCh = query.charAt(i + 1);
                        if (nextCh == '\'') {
                            // A double quote. Skip over.
                            i++;
                        } else {
                            inQuote = false;
                        }
                    } else {
                        inQuote = false;
                    }
                }
            }
        }
        sb.append(query, curIndex, len);

        // Allow semi-colon forgiveness. Can be useful for complex query constructions where the builder
        // may not know they are building the last part of a query.
        if (query.charAt(len - 1) != ';') {
            sb.append(';');
        }
        String convertedQuery = sb.toString();
        return new DnSqlStatement(sqlCxt.cxt.shard, sqlCxt.topic, name, query, convertedQuery, fields, bindFields);
    }

    public static String mkInsertQuery(String tableName, List<DnField> fields, boolean[] hasAutoIncrement) {
        // Note that nulls get dropped.
        List<String> fieldNames = nMapSimple(fields, (fld -> {
            if (fld.isAutoIncrementing()) {
                if (hasAutoIncrement != null && hasAutoIncrement.length > 0) {
                    hasAutoIncrement[0] = true;
                }
                return null;
            }
            return fld.name;
        }));
        return mkInsertQuery2(tableName, fieldNames);
    }

    public static String mkInsertQuery2(String tableName, List<String> fieldNames) {
        String firstPart = "INSERT INTO t:" + tableName + " (c:";
        // First list of fields are prefixed with "c:" so that they will get translated into column names.
        String secondPart = String.join(", c:", fieldNames);
        String thirdPart = ") VALUES (:";
        // Prefix field names with ":" so that they will be treated as named parameters.
        String fourthPart = String.join(", :", fieldNames);
        String lastPart = ")";
        return firstPart + secondPart + thirdPart + fourthPart + lastPart;
    }

    public static String mkSelectQuery(String tableName, List<String> andFields) {
        return "SELECT * FROM t:" + tableName + mkAndClause(andFields, true);
    }

    public static String mkUpdateQuery(String tableName, List<DnField> fields, List<String> andFields) {
        List<String> fieldNames = nMapSimple(fields, (fld -> {
            if (andFields.contains(fld.name)) {
                return null;
            }
            return fld.name;
        }));

        return mkUpdateQuery2(tableName, fieldNames, andFields);

    }

    public static String mkUpdateQuery2(String tableName, List<String> fieldNames, List<String> andFields) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE t:").append(tableName).append(" SET ");
        boolean isFirst = true;
        for (var fld : fieldNames) {
            if (!isFirst) {
                sb.append(", ");
            }
            isFirst = false;
            sb.append("c:").append(fld).append(" = :").append(fld);
        }
        sb.append(mkAndClause(andFields, true));
        return sb.toString();
    }

    public static String mkAndClause(List<String> andFields, boolean isStartOfClause) {
        var sb = new StringBuilder();
        if (andFields.size() > 0) {
            if (isStartOfClause) {
                sb.append(" WHERE ");
            } else {
                sb.append(" AND ");
            }
        }
        appendAndStatements(sb, andFields);
        return sb.toString();
    }

    public static void appendAndStatements(StringBuilder sb, List<String> andFields) {
        boolean isFirst = true;
        for (var s : andFields) {
            if (!isFirst) {
                sb.append(" AND ");
            }
            isFirst = false;
            sb.append("c:").append(s).append(" = :").append(s);
        }
    }

    public static DnException mkDnException(String msg, Exception e) {
        boolean isTransient = e instanceof SQLTransientException;
        String activity = (isTransient) ? DnException.IO : DnException.UNSPECIFIED;
        return new DnException(msg, e, DnException.INTERNAL_ERROR, DnException.DATABASE, activity);
    }

}
