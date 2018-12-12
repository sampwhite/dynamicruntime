package org.dynamicruntime.servlet;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
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
        Server server = new Server(7070);
        log.info(cxt, "Testing logging.");
        new DnServer(server);
        try {
            server.start();
        } catch (Exception e) {
            throw new DnException("Could not start server", e);
        }
        try {
            server.join();
        } catch (InterruptedException ignore) {}
    }

    @Override
    public void doHandle(String target, Request baseRequest, HttpServletRequest request,
            HttpServletResponse response) {
        var handler = new DnRequestHandler(target, request, response);
        handler.handleRequest();
    }



}
