package org.dynamicruntime.servlet;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import static org.dynamicruntime.servlet.LogServlet.*;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class DnServer extends ContextHandler {
    ServletConfig servletConfig;

    public DnServer(Server server) {
        super(server, "/");
    }

    public static void launch() throws DnException {
        Server server = new Server(7070);
        DnCxt cxt = DnCxt.mkSimpleCxt("ServerStartup");
        //ServletContextHandler handler = new ServletContextHandler(server, "/");
        //handler.addServlet(DnServer.class, "/");
        new DnServer(server);
        try {
            server.start();
        } catch (Exception e) {
            throw new DnException("Could not start server");
        }
        try {
            server.join();
        } catch (InterruptedException ignore) {}
    }

    @Override
    public void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        var output = response.getOutputStream();
        var bytes = "Hello".getBytes();
        HttpServletResponse httpRes = (HttpServletResponse)response;
        httpRes.setStatus(201);
        httpRes.setContentLength(bytes.length);
        httpRes.setContentType("text/plain");
        log.debug(null, baseRequest.getHttpURI().toString());

        output.write(bytes);
        httpRes.flushBuffer();
    }



}
