package org.dynamicruntime.schemadata;

@SuppressWarnings("WeakerAccess")
public class CoreConstants {
    //
    // Node health values.
    //
    /** Function name for getting health values. */
    public static final String ND_GET_HEALTH_FUNCTION = "node.getHealth";
    /** Start time. */
    public static final String ND_START_TIME = "nodeStartTime";
    /** Uptime in days (in floating point). */
    public static final String ND_UPTIME = "uptime";
    /** Node ID **/
    public static final String ND_NODE_ID = "nodeId";
    /** Version */
    public static final String ND_VERSION = "version";

    //
    // Schema for schema.
    //
    /** Function name for getting schema type definitions. */
    public static final String SS_GET_TYPE_DEFINITIONS = "schema.getTypeDefinitions";
    public static final String SS_DN_TYPE_NAME = "dnTypeName";
    public static final String SS_NAMESPACE = "namespace";
    /** Function name for getting schema endpoint definitions. */
    public static final String SS_GET_ENDPOINT_DEFINITIONS = "schema.getEndpointDefinitions";
    public static final String SS_ENDPOINT_PATH_PREFIX = "pathPrefix";
    /** Function name for getting schema table definitions. */
    public static final String SS_GET_TABLE_DEFINITIONS = "schema.getTableDefinitions";
    public static final String SS_TABLE_NAME_PREFIX = "namePrefix";
}
