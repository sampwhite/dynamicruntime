package org.dynamicruntime.endpoint;

import org.dynamicruntime.node.DnNodeService;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.schemadef.DnEndpointFunction;

import static org.dynamicruntime.schemadata.CoreConstants.*;
import static org.dynamicruntime.schemadef.DnEndpointFunction.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class NodeEndpoints {
    /** Gets basic health info. */
    static void getHealth(DnRequestCxt requestCxt) {
        var nodeService = Objects.requireNonNull(DnNodeService.get(requestCxt.cxt));
        requestCxt.mapResponse.putAll(nodeService.getHealth());
    }

    /** Bind endpoint code to names so that they can be found to endpoint definitions. */
    public static List<DnEndpointFunction> getFunctions() {
        return mList(mkEndpoint(ND_GET_HEALTH_FUNCTION, NodeEndpoints::getHealth));
    }
}
