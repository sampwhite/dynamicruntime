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
public class NodeSchema {
    public static final String NODE_NAMESPACE = "node";

    public static DnRawField startTime = mkReqDateField(ND_START_TIME, "Start Time",
            "Date and time at which the node started.");
    public static DnRawField uptime = mkReqField(ND_UPTIME, "Uptime",
            "The amount of time that the node has been up.");
    public static DnRawField nodeId = mkReqField(ND_NODE_ID, "Node ID",
            "Unique identifier of node.");
    public static DnRawField isMember = mkReqBoolField(ND_IS_CLUSTER_MEMBER, "Is Cluster Member",
            "Whether node is acting as a member of a cluster.");
    public static DnRawField version = mkReqField(ND_VERSION, "Version",
            "Current version information for the code running the node.");

    public static DnRawType healthInfo = mkType("HealthInfoResponse",
            mList(startTime, startTime, uptime, nodeId, isMember, version));
    public static DnRawEndpoint healthEndpoint = mkEndpoint("/health/info", ND_GET_HEALTH_FUNCTION,
            "Gets basic health status information for the node.", DNT_NONE, healthInfo.name);

    public static DnRawType memberInfo = mkType("NodeClusterMemberInfo", mList(isMember));
    public static DnRawEndpoint membershipEndpoint = mkEndpoint("/node/setClusterMembership",
            ND_SET_CLUSTER_MEMBERSHIP,
            "Sets the cluster membership state.", memberInfo.name, healthInfo.name).setMethod(EPH_PUT);

    public static DnRawSchemaPackage getPackage() {
        return DnRawSchemaPackage.mkPackage("NodeSchema", NODE_NAMESPACE, mList(healthInfo,
                healthEndpoint, memberInfo, membershipEndpoint));
    }
}
