package org.dynamicruntime.sql;


import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnField;
import org.dynamicruntime.schemadef.DnTable;
import org.dynamicruntime.util.EncodeUtils;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.TBI_UNIQUE_INDEX;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("WeakerAccess")
public class SqlTableUtil {
    static class TypeInfo {
        final String name;
        final String dnType;
        final DnField field;
        TypeInfo(String name, String dnType, DnField field) {
            this.name = name;
            this.dnType = dnType;
            this.field = field;
        }
    }

    public static boolean checkCreateTable(SqlCxt sqlCxt, DnTable tableDef) throws DnException {
        if (sqlCxt.sqlDb.hasCreatedTable(sqlCxt, tableDef.tableName)) {
            return false;
        }
        createTable(sqlCxt, tableDef);
        return true;
    }

    /** Creates a table and adds columns and/or indexes if they are missing. This call should be done
     * inside a SqlSession. Returns false if we already have asked to create this table. Caller can
     * use return value to determine if row provisioning logic needs to be run. */
    public static void createTable(SqlCxt sqlCxt, DnTable tableDef) throws DnException {
        var cxt = sqlCxt.cxt;
        SqlDatabase sqlDb = sqlCxt.sqlDb;
        String dbTableName = sqlDb.mkSqlTableName(sqlCxt, tableDef.tableName);

        // Register column aliases.
        sqlDb.addDefaultAliases(sqlCxt.topic, tableDef.columns);

        // Create or update table.
        Connection conn = sqlDb.getMustExist(cxt).getConnection();
        try {
            var dbMetadata = conn.getMetaData();
            ResultSet rs = dbMetadata.getColumns(null, null, dbTableName, null);

            Map<String,TypeInfo> typeInfo = mMapT();

            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (!sqlDb.options.identifiersCaseSensitive && !sqlDb.options.storesLowerCaseIdentifiersInSchema) {
                    // We store our identifiers in lower case if identifiers are not case sensitive.
                    name = name.toLowerCase();
                }
                int jdbcType = rs.getInt("DATA_TYPE");
                String dnType = SqlTypeUtil.toDnType(jdbcType);
                typeInfo.put(name, new TypeInfo(name, dnType, null));
            }

            var aliases = sqlDb.getAliases(sqlCxt.topic);
            if (typeInfo.isEmpty()) {
                // Create table from scratch.
                var sb =  new StringBuilder();
                sb.append("CREATE TABLE ").append(dbTableName).append(" (\n");
                boolean isFirst = true;
                for (var fld : tableDef.columns) {
                    if (!isFirst) {
                        sb.append(",\n");
                    }
                    sb.append(' ');
                    appendFieldDeclaration(sb, sqlCxt, aliases, fld);
                    isFirst = false;
                }
                sb.append(",\n");
                String primaryKeyClause = SqlStmtUtil.createColumnList(sqlCxt, tableDef.primaryKey.fieldDeclarations);
                sb.append(" PRIMARY KEY (").append(primaryKeyClause).append(")\n);");
                String createStmt = sb.toString();
                sqlDb.executeSchemaChangeSql(cxt, createStmt);
            } else {
                // Look for columns to add.
                List<TypeInfo> missingFields = mList();
                for (var fld : tableDef.columns) {
                    var colName = aliases.getColumnName(fld.name);
                    if (!typeInfo.containsKey(colName)) {
                        missingFields.add(new TypeInfo(colName, fld.coreType, fld));
                    }
                }
                if (missingFields.size() > 0) {
                    // Note that we cannot add required fields. ALso, different databases
                    // handle multiple fields differently, so to keep things simple
                    // we do a separate *add* for each column. In theory, we should be
                    // adding columns fairly rarely so efficiency should not matter.
                    for (var ti : missingFields) {
                        var dbType = SqlTypeUtil.toDbType(sqlCxt, ti.field);
                        String alterStmt = "ALTER TABLE " + dbTableName + " ADD COLUMN " + ti.name +
                                " " + dbType;
                        sqlDb.executeSchemaChangeSql(cxt, alterStmt);
                    }
                }
            }
        } catch (SQLException e) {
            throw SqlStmtUtil.mkDnException(String.format("Exception during creation or update of table %s.",
                    dbTableName), e);
        }

        addIndexes(sqlCxt, dbTableName, tableDef.indexes);
        sqlDb.registerHasCreatedSqlTable(dbTableName);
    }

    public static void addIndexes(SqlCxt sqlCxt, String dbTableName, List<DnTable.Index> indexes) throws DnException {
        var sqlDb = sqlCxt.sqlDb;
        var cxt = sqlCxt.cxt;
        var aliases = sqlDb.getAliases(sqlCxt.topic);
        Connection conn = sqlDb.getMustExist(cxt).getConnection();
        Set<String> existingIndexes = new HashSet<>();
        try {
            var dbMetadata = conn.getMetaData();
            ResultSet rs = dbMetadata.getIndexInfo(null, null, dbTableName,
                    false, false);
            String curIndexName = null;
            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                boolean isAddingTo = true;
                if (curIndexName == null || !indexName.equals(curIndexName)) {
                    if (sb.length() > 0) {
                        existingIndexes.add(sb.toString());
                        sb.setLength(0);
                    }
                    curIndexName = indexName;
                    isAddingTo = false;
                }
                String column = rs.getString("COLUMN_NAME");
                if (aliases.toLowerCaseColumns) {
                    column = column.toLowerCase();
                }
                if (isAddingTo) {
                    sb.append(":");
                }
                sb.append(column);
            }
            if (sb.length() > 0) {
                existingIndexes.add(sb.toString());
            }

            for (var index : indexes) {
                List<String> fieldNames = index.fieldNames;
                List<String> colNames = nMapSimple(fieldNames, aliases::getColumnName);
                String key = String.join(":", colNames);
                if (!existingIndexes.contains(key)) {
                    String indexName = index.name;
                    if (indexName == null) {
                        indexName = String.join("_", colNames);
                    }

                    // Turn index name into a global name.
                    String tbIndexName = "idx_" + dbTableName + "_" + indexName;
                    // Limit index name to 60 characters so we do not run into trouble with maximum
                    // identifier lengths.
                    String shortenedName = EncodeUtils.mkUniqueShorterStr(tbIndexName, 60);
                    boolean isUnique = getBoolWithDefault(index.indexProperties, TBI_UNIQUE_INDEX, false);
                    String uniqueStr = (isUnique) ? " UNIQUE" : "";
                    // Currently our building of index is simple. But eventually we may support more indexProperties
                    // and tweak the entries in the *index.columns* based on which database we are creating the
                    // index for.
                    String stmt = "CREATE" + uniqueStr + " INDEX " + shortenedName + " ON " + dbTableName + "(" +
                            SqlStmtUtil.createColumnList(sqlCxt, index.fieldDeclarations) + ")";
                    sqlDb.executeSchemaChangeSql(cxt, stmt);

                }
            }

        } catch (SQLException e) {
            throw SqlStmtUtil.mkDnException(
                    String.format("Exception adding indexes for table %s.", dbTableName), e);
        }
    }

    public static void appendFieldDeclaration(StringBuilder sb, SqlCxt sqlCxt, SqlColumnAliases aliases, DnField fld) {
        var colName = aliases.getColumnName(fld.name);
        var dbType = SqlTypeUtil.toDbType(sqlCxt, fld);
        var extra = (fld.isRequired) ? " not null" : "";
        sb.append(colName).append(" ").append(dbType).append(extra);
    }
}
