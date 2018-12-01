package org.dynamicruntime.servlet;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import static org.dynamicruntime.servlet.LogServlet.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@SuppressWarnings("WeakerAccess")
public class DnServer extends ContextHandler {
    public DnServer(Server server) {
        super(server, "/");
    }

    public static void launch(DnCxt cxt) throws DnException {
        Server server = new Server(7070);
        log.info(cxt, "Testing logging.");
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
            throws IOException {
        var output = response.getOutputStream();
        var bytes = "Hello".getBytes();

        response.setStatus(201);
        response.setContentLength(bytes.length);
        response.setContentType("text/plain");
        log.debug(null, baseRequest.getHttpURI().toString());

        output.write(bytes);
        response.flushBuffer();
    }



}
