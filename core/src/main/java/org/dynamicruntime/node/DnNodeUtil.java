package org.dynamicruntime.node;

import org.dynamicruntime.context.DnCxt;

@SuppressWarnings("WeakerAccess")
public class DnNodeUtil {
    public static final int DEFAULT_PORT = 7070;

    @SuppressWarnings("unused")
    public static DnNodeId extractNodeId(DnCxt cxt) {
        int port = DEFAULT_PORT; // Hardwire for now.
        String ipAddress = System.getenv("NODE_IP_ADDRESS");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = "127.0.0.1";
        }
        String hostName = System.getenv("HOSTNAME");
        if (hostName == null || hostName.isEmpty() || hostName.equals("ubuntu")) {
            hostName = "localhost";
        }
        return new DnNodeId(ipAddress, hostName, port);
    }
}
