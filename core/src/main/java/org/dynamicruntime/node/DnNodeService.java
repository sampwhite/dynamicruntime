package org.dynamicruntime.node;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.startup.ServiceInitializer;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.schemadata.CoreConstants.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnNodeService implements ServiceInitializer {
    public static final Date VM_STARTTIME = new Date();

    public static final String DN_NODE_SERVICE = DnNodeService.class.getSimpleName();

    private DnNodeId nodeId;
    private String nodeIdLabel;
    public volatile boolean isInCluster = true;
    public boolean loggingHealthChecks = false;

    public static DnNodeService get(DnCxt cxt) {
        Object obj = cxt.instanceConfig.get(DN_NODE_SERVICE);
        return (obj instanceof DnNodeService) ? (DnNodeService)obj : null;
    }

    @Override
    public String getServiceName() {
        return DN_NODE_SERVICE;
    }

    @Override
    public void onCreate(DnCxt cxt) {
        nodeId = DnNodeUtil.extractNodeId(cxt);
        nodeIdLabel = nodeId.nodeIpAddress + ":" + nodeId.port;
        loggingHealthChecks = toBoolWithDefault(cxt.instanceConfig.get("loggingHealthChecks"), false);
    }

    @Override
    public void checkInit(DnCxt cxt) {

    }

    public DnNodeId getNodeId() {
        return nodeId;
    }

    public String getNodeLabel() {
        return nodeIdLabel;
    }

    public Map<String,Object> getHealth() {
        long vt = VM_STARTTIME.getTime();
        long curTime = System.currentTimeMillis();
        double durInDays = ((double)(curTime - vt))/(1000*24*3600);
        String durRpt = fmtDouble(durInDays) + " days";
        return mMap(ND_START_TIME, VM_STARTTIME, ND_UPTIME, durRpt, ND_NODE_ID, nodeIdLabel,
                ND_IS_CLUSTER_MEMBER, isInCluster, ND_VERSION, "0.2");
    }
}
