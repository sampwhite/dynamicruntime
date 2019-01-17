package org.dynamicruntime.servlet;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.node.DnNodeId;
import org.dynamicruntime.node.DnNodeUtil;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import static org.dynamicruntime.servlet.LogServlet.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("WeakerAccess")
public class DnServer extends ContextHandler {

    public DnServer(Server server) {
        super(server, "/");
    }

    public static void launch(DnCxt cxt) throws DnException {
        DnNodeId nodeId = DnNodeUtil.extractNodeId(cxt);

        Server server = new Server(nodeId.port);
        new DnServer(server);
        try {
            server.start();
            LogServlet.log.debug(cxt, String.format("Started server at %s:%d on hostname %s.",
                    nodeId.nodeIpAddress, nodeId.port, nodeId.hostname));
            try {
                server.join();
            } catch (InterruptedException ignore) {}
        } catch (Exception e) {
            throw new DnException("Could not start server", e);
        }
     }

    @Override
    public void doHandle(String target, Request baseRequest, HttpServletRequest request,
            HttpServletResponse response) {
        var handler = new DnRequestHandler(target, request, response);
        handler.handleRequest();
    }



}
