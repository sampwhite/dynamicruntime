package org.dynamicruntime.endpoint;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.node.DnNodeService;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.schemadef.DnEndpointFunction;

import static org.dynamicruntime.schemadata.CoreConstants.*;
import static org.dynamicruntime.schemadef.DnEndpointFunction.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class NodeEndpoints {
    /** Gets basic health info. */
    static void getHealth(DnRequestCxt requestCxt) throws DnException {
        var nodeService = Objects.requireNonNull(DnNodeService.get(requestCxt.cxt));
        if (requestCxt.requestInfo != null && requestCxt.requestInfo.isFromLoadBalancer) {
            if (!nodeService.isInCluster) {
                throw new DnException(
                        String.format("Node %s is not acting as part of the cluster.", nodeService.getNodeLabel()),
                        null, DnException.NOT_SUPPORTED, DnException.SYSTEM, DnException.CONNECTION);
            }
            if (!nodeService.loggingHealthChecks) {
                requestCxt.logRequest = false;
            }
        }
        requestCxt.mapResponse.putAll(nodeService.getHealth());
    }

    static void setClusterMembership(DnRequestCxt requestCxt) throws DnException {
        boolean setMembership = getBoolWithDefault(requestCxt.requestData, ND_IS_CLUSTER_MEMBER, true);
        var nodeService = Objects.requireNonNull(DnNodeService.get(requestCxt.cxt));
        nodeService.isInCluster = setMembership;
        requestCxt.mapResponse.putAll(nodeService.getHealth());
    }

    /** Bind endpoint code to names so that they can be found to endpoint definitions. */
    public static List<DnEndpointFunction> getFunctions() {
        return mList(mkEndpoint(ND_GET_HEALTH_FUNCTION, NodeEndpoints::getHealth),
                mkEndpoint(ND_SET_CLUSTER_MEMBERSHIP, NodeEndpoints::setClusterMembership));
    }
}
