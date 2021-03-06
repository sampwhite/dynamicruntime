package org.dynamicruntime.schemadef;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.startup.StartupServiceInitializer;
import org.dynamicruntime.util.ConvertUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.schemadef.LogSchema.*;

@SuppressWarnings("WeakerAccess")
public class DnSchemaService implements StartupServiceInitializer {
    public static final String DN_SCHEMA_SERVICE = DnSchemaService.class.getSimpleName();
    public DnRawSchemaStore rawSchemaStore;
    public final AtomicReference<DnSchemaStore> schemaStore = new AtomicReference<>();
    public boolean isCreated = false;
    public boolean isInit = false;

    public static DnSchemaService get(DnCxt cxt) {
        var obj = cxt.instanceConfig.get(DN_SCHEMA_SERVICE);
        return (obj instanceof DnSchemaService) ? (DnSchemaService)obj : null;
    }

    @Override
    public String getServiceName() {
        return DN_SCHEMA_SERVICE;
    }

    public void onCreate(DnCxt cxt) throws DnException {
        if (isCreated) {
            return;
        }
        rawSchemaStore = DnRawSchemaStore.get(cxt);
        if (rawSchemaStore == null) {
            throw new DnException("Raw schema store not create for *DnSchemaService*.");
        }
        rawSchemaStore.builders.put(EP_ENDPOINT, this::buildEndpointType);
        rawSchemaStore.builders.put(TB_TABLE, this::buildTableDefinition);

        // Add some built-in types.
        var count = DnRawType.mkSubType(DNT_COUNT, DNT_INTEGER, null)
                .setAttribute(DN_MIN, 0);
        var corePckg = DnRawSchemaPackage.mkPackage("DnSchemaServiceCore", DN_CORE_NAMESPACE,
                mList(count));
        rawSchemaStore.addPackage(corePckg);
        isCreated = true;
    }

    @Override
    public void checkInit(DnCxt cxt) throws DnException {
        if (isInit) {
            return;
        }

        // Create the non-raw schema store..
        createSchemaStore(cxt);

        isInit = true;
    }

    public DnSchemaStore getSchemaStore() {
        return schemaStore.get();
    }

    public void createSchemaStore(DnCxt cxt) throws DnException {
        log.debug(cxt, "Creating read only schema store from raw modifiable data inputs.");
        synchronized (this) {
            // Eventually we will extract DnEndpoints and DnTables as well.
            Map<String,DnType> dnTypes = mMapT();
            Map<String,DnEndpoint> endpoints = mMapT();
            Map<String,DnTable> tables = mMapT();

            // Loop twice. First time just the builders, and then everybody else.
            Map<String,DnRawType> changedTypes = mMapT();
            for (int i = 0; i < 2; i++) {
                if (changedTypes.size() > 0) {
                    rawSchemaStore.rawTypes.putAll(changedTypes);
                }
                for (DnRawType rawTypeIn : rawSchemaStore.rawTypes.values()) {
                    String builder = getOptStr(rawTypeIn.model, DN_BUILDER);
                    boolean doBuilder = (i == 0);
                    boolean hasBuilder = builder != null;
                    if (hasBuilder != doBuilder) {
                        continue;
                    }
                    DnRawType rawType = rawTypeIn;
                    if (hasBuilder) {
                        DnBuilder builderFunction = rawSchemaStore.builders.get(builder);
                        if (builderFunction == null) {
                            throw DnException.mkConv(String.format("Unable to find implementation of builder " +
                                    "%s for type %s.", builder, rawTypeIn.name));
                        }
                        rawType = builderFunction.buildType(cxt, rawTypeIn);
                        rawType.finish();
                        changedTypes.put(rawType.name, rawType);
                    }
                    DnType dnType = DnType.extract(rawType.model, rawSchemaStore.rawTypes);
                    dnTypes.put(dnType.name, dnType);
                    boolean isEndpoint = getBoolWithDefault(dnType.model, DN_IS_ENDPOINT, false);
                    if (isEndpoint) {
                        String functionName = getReqStr(dnType.model, EP_FUNCTION);
                        var endpointFunction = rawSchemaStore.functions.get(functionName);
                        if (endpointFunction == null) {
                            throw DnException.mkConv(String.format("Unable to find function %s " +
                                    "for dnType %s.", functionName, rawTypeIn.name));
                        }
                        DnEndpoint endpoint = DnEndpoint.extract(dnType, endpointFunction);
                        endpoints.put(endpoint.path, endpoint);
                    }
                    boolean isTable = getBoolWithDefault(dnType.model, DN_IS_TABLE, false);
                    if (isTable) {
                        DnTable table = DnTable.extract(dnType);
                        tables.put(table.tableName, table);
                    }

                }
                schemaStore.set(new DnSchemaStore(dnTypes, endpoints, tables));
            }
        }
    }

    /** Builds an endpoint type from specialized inputs in extra attributes of
     * a raw type. See the *DynamicType.md* for a description of the transformation.
     * Note: This method is likely to grow in complexity over time as more subtleties get added
     * to the general endpoint functionality. */
    public DnRawType buildEndpointType(@SuppressWarnings("unused") DnCxt cxt, DnRawType epInputType)
            throws DnException {
        var inModel = epInputType.model;
        var namespace = epInputType.namespace;
        String inputTypeRef = DnTypeUtils.applyNamespace(namespace, getReqStr(inModel, EP_INPUT_TYPE_REF));
        String outputTypeRef = DnTypeUtils.applyNamespace(namespace, getReqStr(inModel, EP_OUTPUT_TYPE_REF));

        DnRawField inputField = DnRawField.mkField(EPF_INPUT_TYPE,
                "Endpoint Input Type", "The definition of the validation and transformation to " +
                        "be applied to input data for the endpoint.");
        DnRawField outputField = DnRawField.mkField(EPF_OUTPUT_TYPE, "Endpoint Output Type",
                "The specification of the allowable output for this endpoint.");
        DnRawField requestUri = DnRawField.mkReqField(EPR_REQUEST_URI, "Request URI",
                "The request URI that make this request.");
        DnRawField duration = DnRawField.mkReqField(EPR_DURATION, "Duration in Milliseconds",
                "The time taken to perform the request in milliseconds.").setTypeRef(DNT_FLOAT);

        if (getBoolWithDefault(inModel, EP_IS_LIST_RESPONSE, false)) {
            int defaultLimit = 100;
            // More complex result.
            var inputType = DnRawType.mkSubType(inputTypeRef);
            var limitType = DnRawType.mkSubType(DNT_COUNT).setAttribute(DN_MAX, 20000);
            if (!getBoolWithDefault(inModel, EP_NO_LIMIT_PARAMETER, false)) {
                DnRawField limit = DnRawField.mkField(EPF_LIMIT, "Limit On Results",
                        "The maximum number of items that can be returned.")
                        .setTypeDef(limitType)
                        .setAttribute(DN_DEFAULT_VALUE, defaultLimit);
                inputType.addField(limit);
            }

            inputField.setTypeDef(inputType);

            DnRawField numItems = DnRawField.mkReqField(EPR_NUM_ITEMS, "Number of Items",
                    "Number of items returned.").setTypeRef(DNT_COUNT);
            DnRawField items = DnRawField.mkReqField(EPR_ITEMS, "Items",
                    "Items returned by endpoint.")
                    .setTypeRef(outputTypeRef).setAttribute(DN_IS_LIST, true);
            List<DnRawField> fieldList = mList(numItems, requestUri, duration);
            if (getBoolWithDefault(inModel, EP_HAS_MORE_PAGING, false)) {
                DnRawField hasMore = DnRawField.mkReqBoolField(EPR_HAS_MORE, "Has More",
                        "Whether there are more items that could be returned.");
                fieldList.add(hasMore);
            }
            if (getBoolWithDefault(inModel, EP_HAS_NUM_AVAILABLE, false)) {
                DnRawField totalSize = DnRawField.mkReqField(EPR_NUM_AVAILABLE, "Total Size",
                        "The total number of items available to be returned.").setTypeRef(DNT_COUNT);
                fieldList.add(totalSize);
            }
            fieldList.add(items);
            var outputType = DnRawType.mkType(fieldList);
            outputField.setTypeDef(outputType);
        } else {
            // Endpoints always use inline (unregistered) types at their core.
            inputField.setTypeDef(DnRawType.mkSubType(inputTypeRef));
            var outputType = DnRawType.mkSubType(outputTypeRef);
            outputType.addFields(mList(requestUri, duration));
            outputField.setTypeDef(outputType);
        }

        DnRawType endpointType = DnRawType.mkType(epInputType.name, null);
        endpointType.model.putAll(epInputType.model);
        endpointType.addFields(mList(inputField, outputField));
        endpointType.setAttribute(DN_IS_ENDPOINT, true);
        return endpointType;
    }

    public DnRawType buildTableDefinition(@SuppressWarnings("unused") DnCxt cxt, DnRawType tbInputType)
            throws DnException {
        var inModel = tbInputType.model;
        String tableName = getReqStr(inModel, TB_NAME);
        List<Map<String,Object>> indexes = mList();
        List<DnRawField> fields = mList();

        var existingFields = nMapSimple(getListOfMapsDefaultEmpty(inModel, DN_FIELDS), DnRawField::mkRawField);

        String idFieldName = getOptStr(inModel, TB_COUNTER_FIELD);
        Map<String,Object> primaryKey;
        /* See if ID field needs to be added. */
        if (idFieldName != null) {
            DnRawField idField = findItem(existingFields, (fld -> fld.name.equals(idFieldName)));
            if (idField == null) {
                idField = DnRawField.mkReqField(idFieldName, "Auto Counter",
                        "Auto counter field that has primary key defined on it.").setTypeRef(DNT_COUNT);
                fields.add(idField);
            }
            idField.setAttribute(DN_IS_AUTO_INCREMENTING, true);
            primaryKey = mMap(TBI_INDEX_FIELDS, mList(idFieldName));
        } else {
            primaryKey = buildIndex(inModel.get(TB_PRIMARY_KEY));
            if (primaryKey == null) {
                throw DnException.mkConv(String.format("Table %s must have a primary key.", tableName));
            }
        }

        /* See if user fields need to be added.. */
        boolean isUserData = getBoolWithDefault(inModel, TB_IS_USER_DATA, false);
        if (isUserData) {
            var userField = DnRawField.mkReqField(USER_ID, "User ID", "Unique numeric " +
                    "user ID for user.").setTypeRef(DNT_COUNT);
            var groupField = DnRawField.mkReqField(USER_GROUP, "User Group",
                    "The container group that the user belongs to that is used to determine " +
                            "sharding, UI experience, and logic rules for the user.");
            fields.add(userField);
            fields.add(groupField);
        }

        /* Add the fields given to us. */
        fields.addAll(existingFields);

        /* See if the modifyUser should be added. */
        boolean hasModifyUser = getBoolWithDefault(inModel, TB_HAS_MODIFY_USER, false);
        if (hasModifyUser) {
            var modifyUserField = DnRawField.mkReqIntField(MODIFY_USER, "Modify User",
                    "User ID of user that last edited the data.");
            fields.add(modifyUserField);
        }

        int lateRank = DN_DEFAULT_SORT_RANK + 50;
        /* See if transaction fields should be added. */
        boolean isTopLevel = getBoolWithDefault(inModel, TB_IS_TOP_LEVEL, false);
        if (isTopLevel) {
            var touchedField = DnRawField.mkReqDateField(TOUCHED_DATE, "Touched Date",
                    "The last time a transaction lock was attempted against this table.");
            var lastTranId = DnRawField.mkReqField(LAST_TRAN_ID, "Last Transaction ID",
                    "The identifier of the transaction that last successfully concluded a transaction.");
            fields.add(touchedField.setRank(lateRank));
            fields.add(lastTranId.setRank(lateRank));
        }

        /* See if enabled field should be suppressed. */
        boolean noEnabled = getBoolWithDefault(inModel, TB_NO_ENABLED, false);
        if (!noEnabled) {
            var enabledField = DnRawField.mkReqBoolField(ENABLED, "Enabled",
                    "Whether the row is enabled.");
            fields.add(enabledField.setRank(lateRank));
        }

        /* See if row tracking date fields should be added. */
        boolean addRowDates = getBoolWithDefault(inModel, TB_HAS_ROW_DATES, false);
        if (addRowDates) {
            var createdDate = DnRawField.mkReqDateField(CREATED_DATE, "Created Date",
                    "When this row was created.");
            var modifiedDate = DnRawField.mkReqDateField(MODIFIED_DATE, "Modified Date",
                    "When this row was last modified.");
            fields.add(createdDate.setRank(lateRank));
            fields.add(modifiedDate.setRank(lateRank));
            var modifiedIndex = buildIndex(mMap(DN_NAME, "ModifiedDate",
                    TBI_INDEX_FIELDS, mList(MODIFIED_DATE)));
            indexes.add(modifiedIndex);
            if (isUserData) {
                var groupModifiedIndex = buildIndex(mMap(DN_NAME, "GroupModifiedDate",
                        TBI_INDEX_FIELDS, mList(USER_GROUP, MODIFIED_DATE)));
                indexes.add(groupModifiedIndex);
            }
        }

        Object indexesObj = inModel.get(TB_INDEXES);
        if (indexesObj instanceof List) {
            List<?> indexesList = (List)indexesObj;
            var ids = nMapSimple(indexesList, DnSchemaService::buildIndex);
            indexes.addAll(ids);
        }

        // Make sure primary key fields go at beginning. Use the DnTable.Index.extract to get
        // index fields.
        var pi = DnTable.Index.extract(primaryKey);
        Map<String,DnRawField> primaryFields = mMapT();
        List<DnRawField> remainingFields = mList();
        for (var fld : fields) {
            // We support repeated fields in list with only earlier field being put in primary key segment.
            // We keep the duplication, because the schema type calculation allows merging
            // of data from repeated fields.
            if (pi.fieldNames.contains(fld.name) && !primaryFields.containsKey(fld.name)) {
                primaryFields.put(fld.name, fld);
            } else {
                remainingFields.add(fld);
            }
        }
        List<DnRawField> sortedFields = mList();
        for (var fldName : pi.fieldNames) {
            var fld = primaryFields.get(fldName);
            if (fld == null) {
                throw DnException.mkConv(String.format("Primary key refers to field %s that does not exist in table %s.",
                        fldName, tableName));
            }
            // Move fields earlier in the sort rank.
            sortedFields.add(fld.setRank(DN_DEFAULT_SORT_RANK - 50));
        }
        sortedFields.addAll(remainingFields);

        DnRawType tableType = DnRawType.mkType(tbInputType.name, mList());
        tableType.model.putAll(inModel);
        tableType.addFields(sortedFields);
        tableType.setAttribute(TB_PRIMARY_KEY, primaryKey);
        tableType.setAttribute(TB_INDEXES, indexes);
        tableType.setAttribute(DN_IS_TABLE, true);
        return tableType;
    }

    public static Map<String,Object> buildIndex(Object obj) {
        if (obj instanceof List) {
            List<?> l = (List<?>) obj;
            var strs = nMapSimple(l, ConvertUtil::toOptStr);
            if (strs.size() == 0) {
                 return null;
            }
            return mMap(TBI_INDEX_FIELDS, strs);
        } else {
            return  toOptMap(obj);
        }
    }
}
