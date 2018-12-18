package org.dynamicruntime.schemadef;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.startup.ServiceInitializer;
import org.dynamicruntime.util.ConvertUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.schemadef.LogSchema.*;

@SuppressWarnings("WeakerAccess")
public class DnSchemaService implements ServiceInitializer {
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
        var count = DnRawType.mkSubType(DN_COUNT, DN_INTEGER)
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
        log.debug(cxt, "Creating schema store from data inputs.");
        synchronized (this) {
            // Eventually we will extract DnEndpoints and DnTables as well.
            Map<String,DnType> dnTypes = mMapT();
            Map<String,DnEndpoint> endpoints = mMapT();
            Map<String,DnTable> tables = mMapT();
            for (DnRawType rawTypeIn : rawSchemaStore.rawTypes.values()) {
                String builder = getOptStr(rawTypeIn.model, DN_BUILDER);
                DnRawType rawType = rawTypeIn;
                if (builder != null) {
                    DnBuilder builderFunction = rawSchemaStore.builders.get(builder);
                    if (builderFunction == null) {
                        throw DnException.mkConv(String.format("Unable to find implementation of builder " +
                                "%s for type %s.", builder, rawTypeIn.name));
                    }
                    rawType = builderFunction.buildType(cxt, rawTypeIn);
                    rawType.finish();
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

        DnRawField inputField = DnRawField.mkField(EP_INPUT_TYPE,
                "Endpoint Input Type", "The definition of the validation and transformation to " +
                        "be applied to input data for the endpoint.");
        DnRawField outputField = DnRawField.mkField(EP_OUTPUT_TYPE, "Endpoint Output Type",
                "The specification of the allowable output for this endpoint.");
        DnRawField requestUri = DnRawField.mkReqField(EP_REQUEST_URI, "Request URI",
                "The request URI that make this request.");
        DnRawField duration = DnRawField.mkReqField(EP_DURATION, "Duration in Milliseconds",
                "The time taken to perform the request in milliseconds.").setTypeRef(DN_FLOAT);

        if (getBoolWithDefault(inModel, EP_IS_LIST_RESPONSE, false)) {
            int defaultLimit = 100;
            // More complex result.
            var inputType = DnRawType.mkSubType(inputTypeRef);
            var limitType = DnRawType.mkSubType(DN_COUNT).setAttribute(DN_MAX, 20000);
            DnRawField limit = DnRawField.mkField(EP_LIMIT, "Limit On Results",
                    "The maximum number of items that can be returned.")
                    .setTypeDef(limitType)
                    .setAttribute(DN_DEFAULT_VALUE, defaultLimit);

            inputType.addField(limit);
            inputField.setTypeDef(inputType);

            DnRawField numItems = DnRawField.mkReqField(EP_NUM_ITEMS, "Number of Items",
                    "Number of items returned.").setTypeRef(DN_COUNT);
            DnRawField items = DnRawField.mkReqField(EP_ITEMS, "Items",
                    "Items returned by endpoint.")
                    .setTypeRef(outputTypeRef).setAttribute(DN_IS_LIST, true);
            List<DnRawField> fieldList = mList(numItems, requestUri, duration);
            if (getBoolWithDefault(inModel, EP_HAS_MORE_PAGING, false)) {
                DnRawField hasMore = DnRawField.mkReqBoolField(EP_HAS_MORE, "Has More",
                        "Whether there are more items that could be returned.");
                fieldList.add(hasMore);
            }
            if (getBoolWithDefault(inModel, EP_HAS_NUM_AVAILABLE, false)) {
                DnRawField totalSize = DnRawField.mkReqField(EP_NUM_AVAILABLE, "Total Size",
                        "The total number of items available to be returned.").setTypeRef(DN_COUNT);
                fieldList.add(totalSize);
            }
            fieldList.add(items);
            var outputType = DnRawType.mkType(fieldList);
            outputField.setTypeDef(outputType);
        } else {
            // Endpoints always use anonymous types at their core.
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

        String idFieldName = getOptStr(inModel, TB_COUNTER_FIELD);
        Map<String,Object> primaryKey;
        /* See if ID field needs to be added. */
        if (idFieldName != null) {
            DnRawField idField = DnRawField.mkReqIntField(idFieldName, "Auto Counter",
                    "Auto counter field that has primary key defined on it.");
            idField.setAttribute(DN_IS_AUTO_INCREMENTING, true);
            fields.add(idField);
            primaryKey = mMap(TB_INDEX_FIELDS, mList(idFieldName));
        } else {
            primaryKey = buildIndex(inModel.get(TB_PRIMARY_KEY));
            if (primaryKey == null) {
                throw DnException.mkConv(String.format("Table %s must have a primary key.", tableName));
            }
        }

        /* See if user fields need to be added.. */
        boolean isUserData = getBoolWithDefault(inModel, TB_IS_USER_DATA, false);
        if (isUserData) {
            var userField = DnRawField.mkReqIntField(USER_ID, "User ID", "Unique numeric " +
                    "user ID for user.");
            var groupField = DnRawField.mkReqField(USER_GROUP, "User Group",
                    "The container group that the user belongs to that is used to determine " +
                            "sharding, UI experience, and logic rules for the user.");
            fields.add(userField);
            fields.add(groupField);
        }

        /* Add the fields given to us. */
        var existingFields = nMapSimple(getListOfMapsDefaultEmpty(inModel, DN_FIELDS), DnRawField::mkRawField);
        fields.addAll(existingFields);

        /* See if the modifyUser should be added. */
        boolean hasModifyUser = getBoolWithDefault(inModel, TB_HAS_MODIFY_USER, false);
        if (hasModifyUser) {
            var modifyUserField = DnRawField.mkReqIntField(MODIFY_USER, "Modify User",
                    "User ID of user that last edited the data.");
            fields.add(modifyUserField);
        }

        /* See if enabled field should be suppressed. */
        boolean noEnabled = getBoolWithDefault(inModel, TB_NO_ENABLED, false);
        if (!noEnabled) {
            var enabledField = DnRawField.mkReqBoolField(ENABLED, "Enabled",
                    "Whether the row is enabled.");
            fields.add(enabledField);
        }

        /* See if row tracking date fields should be added. */
        boolean addRowDates = getBoolWithDefault(inModel, TB_HAS_ROW_DATES, false);
        if (addRowDates) {
            var createdDate = DnRawField.mkReqDateField(CREATED_DATE, "Created Date",
                    "When this row was created.");
            var modifiedDate = DnRawField.mkReqDateField(MODIFIED_DATE, "Modified Date",
                    "When this row was last modified.");
            fields.add(createdDate);
            fields.add(modifiedDate);
            var modifiedIndex = buildIndex(mMap(DN_NAME, "ModifiedDate",
                    TB_INDEX_FIELDS, mList(MODIFIED_DATE)));
            indexes.add(modifiedIndex);
            if (isUserData) {
                var groupModifiedIndex = buildIndex(mMap(DN_NAME, "GroupModifiedDate",
                        TB_INDEX_FIELDS, mList(USER_GROUP, MODIFIED_DATE)));
                indexes.add(groupModifiedIndex);
            }
        }

        Object indexesObj = inModel.get(TB_INDEXES);
        if (indexesObj instanceof List) {
            List<?> indexesList = (List)indexesObj;
            var ids = nMapSimple(indexesList, DnSchemaService::buildIndex);
            indexes.addAll(ids);
        }

        DnRawType tableType = DnRawType.mkType(tbInputType.name, mList());
        tableType.model.putAll(inModel);
        tableType.addFields(fields);
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
            return mMap(TB_INDEX_FIELDS, strs);
        } else {
            return  toOptMap(obj);
        }
    }
}
