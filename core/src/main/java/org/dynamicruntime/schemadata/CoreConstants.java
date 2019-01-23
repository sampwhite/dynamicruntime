package org.dynamicruntime.schemadata;

@SuppressWarnings("WeakerAccess")
public class CoreConstants {
    // Node schema namespace
    public static final String ND_NAMESPACE = "node";
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
    // Node configuration values.
    //
    /** Unique identifier for instance. */
    public static final String ND_INSTANCE_NAME = "instanceName";
    /** The type of instance configuration. */
    public static final String ND_CONFIG_TYPE = "configType";
    /** The name of the configuration data. */
    public static final String ND_CONFIG_NAME = "configName";
    /** The configuration data package. */
    public static final String ND_CONFIG_DATA = "configData";

    // Currently known node configuration types.
    /** Auth data type. */
    public static final String NDC_AUTH_CONFIG = "authConfig";

    /** The encryption key to create user authentication cookies. */
    public static final String ND_ENCRYPTION_KEY = "encryptionKey";

    //
    // Node endpoints.
    //
    /** Function name for toggling node cluster membership. */
    public static final String ND_SET_CLUSTER_MEMBERSHIP = "node.setClusterMembership";
    /** Parameter for setting clusterMemberState *. */
    public static final String ND_IS_CLUSTER_MEMBER = "isClusterMember";

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
