package org.dynamicruntime.node;

public class DnNodeId {
    public final String nodeIpAddress;
    public final String hostname;
    public final int port;

    public DnNodeId(String nodeIpAddress, String hostname, int port) {
        this.nodeIpAddress = nodeIpAddress;
        this.hostname = hostname;
        this.port = port;
    }
}
