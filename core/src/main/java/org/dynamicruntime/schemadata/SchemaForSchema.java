package org.dynamicruntime.schemadata;

import org.dynamicruntime.schemadef.DnRawEndpoint;
import org.dynamicruntime.schemadef.DnRawField;
import org.dynamicruntime.schemadef.DnRawSchemaPackage;
import org.dynamicruntime.schemadef.DnRawType;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.schemadata.CoreConstants.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnRawType.*;
import static org.dynamicruntime.schemadef.DnRawField.*;
import static org.dynamicruntime.schemadef.DnRawEndpoint.*;

@SuppressWarnings("WeakerAccess")
public class SchemaForSchema {
    public static final String SCHEMA_NAMESPACE = "schema";

    public static DnRawField dnTypeName = mkField(SS_DN_TYPE_NAME, "DnType Name",
            "Name of the type.");
    public static DnRawField namespace = mkField(SS_NAMESPACE, "Namespace",
            "The namespace for the types.");
    public static DnRawType schemaTypeReq = mkType("SchemaTypeRequest",
            mList(dnTypeName, namespace));
    public static DnRawEndpoint getSchemaTypesEndpoint = mkListEndpoint("/schema/dnType/list",
            SS_GET_TYPE_DEFINITIONS, "Gets the definitions of schema DnTypes.",
            schemaTypeReq.name, DNT_MAP)
                .setAttribute(EP_HAS_NUM_AVAILABLE, true);

    public static DnRawField pathPrefix = mkField(SS_ENDPOINT_PATH_PREFIX, "Path Prefix",
            "Endpoint path prefix to apply as a filter to the results.");
    public static DnRawType schemaEndpointReq = mkType("SchemaEndpointRequest",
            mList(pathPrefix));
    public static DnRawEndpoint getSchemaEndpointsEndpoint = mkListEndpoint("/schema/endpoint/list",
            SS_GET_ENDPOINT_DEFINITIONS, "Gets the schema definitions of endpoints.",
            schemaEndpointReq.name, DNT_MAP)
                .setAttribute(EP_HAS_NUM_AVAILABLE, true);

    public static DnRawField tableNamePrefix = mkField(SS_TABLE_NAME_PREFIX, "Name Prefix",
            "A prefix filter to apply to the table names.");
    public static DnRawType schemaTableReq = mkType("SchemaTableRequest",
            mList(tableNamePrefix));
    public static DnRawEndpoint getSchemaTablesEndpoints = mkListEndpoint("/schema/table/list",
            SS_GET_TABLE_DEFINITIONS, "Gets the schema definitions of tables.",
            schemaTableReq.name, DNT_MAP).setAttribute(EP_HAS_NUM_AVAILABLE, true);

    public static DnRawSchemaPackage getPackage() {
        return DnRawSchemaPackage.mkPackage("SchemaForSchema", SCHEMA_NAMESPACE, mList(
                schemaTypeReq, getSchemaTypesEndpoint,
                schemaEndpointReq, getSchemaEndpointsEndpoint,
                schemaTableReq, getSchemaTablesEndpoints));
    }

}
