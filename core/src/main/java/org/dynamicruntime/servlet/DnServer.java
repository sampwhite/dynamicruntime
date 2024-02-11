package org.dynamicruntime.servlet;

import org.apache.http.HttpVersion;
import org.dynamicruntime.config.ConfigLoadUtil;
import org.dynamicruntime.context.DnConfigUtil;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.node.DnNodeId;
import org.dynamicruntime.node.DnNodeUtil;
import org.dynamicruntime.util.StrUtil;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.dynamicruntime.util.DnCollectionUtil.mList;

@SuppressWarnings("WeakerAccess")
public class DnServer extends ContextHandler {

    public DnServer(Server server) {
        super(server, "/");
    }

    static class DnHandler extends HandlerWrapper {
        public final List<String> otherVirtualHosts;
        public DnHandler(List<String> otherVirtualHosts) {
            this.otherVirtualHosts = otherVirtualHosts;
        }

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request,
                HttpServletResponse response) throws IOException, ServletException {
            var handler = new DnRequestHandler(target, request, response);
            String targetHost = request.getServerName();
            if (isOurHost(targetHost)) {
                request.getServerName();
                handler.handleRequest();
                baseRequest.setHandled(true);
            } else {
                super.handle(target, baseRequest, request, response);
            }
        }

        public boolean isOurHost(String targetHost) {
            if (targetHost == null) {
                return true;
            }
            for (var otherHost : otherVirtualHosts) {
                if (targetHost.contains(otherHost)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static void launch(DnCxt cxt) throws DnException {
        DnNodeId nodeId = DnNodeUtil.extractNodeId(cxt);

        Server server = new Server(nodeId.port);
        new DnServer(server);
        var sslPath = DnConfigUtil.getConfigString(cxt, "ssl.keystore", null, "Location of SSL Key Store");
        var sslPassword = DnConfigUtil.getPrivateStr(cxt, "ssl.password");
        if (sslPath != null && sslPassword != null && !sslPassword.isEmpty()) {
            File f = ConfigLoadUtil.findConfigFile(sslPath);
            if (!f.exists()) {
                throw new DnException("SSL file cannot be found at path " + sslPath + ".");
            }
            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            try {
                int securePort = 8443;
                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setSecureScheme("https");
                httpConfig.setSecurePort(securePort);
                httpConfig.setOutputBufferSize(32768);
                httpConfig.setRequestHeaderSize(8192);
                httpConfig.setResponseHeaderSize(8192);
                httpConfig.setSendServerVersion(true);
                httpConfig.setSendDateHeader(false);
                String keyStorePath = f.getCanonicalPath();
                sslContextFactory.setKeyStorePath(keyStorePath);
                sslContextFactory.setKeyStorePassword(sslPassword);
                sslContextFactory.setKeyManagerPassword(sslPassword);
                sslContextFactory.setTrustStorePath(keyStorePath);
                sslContextFactory.setTrustStorePassword(sslPassword);

                // SSL HTTP Configuration
                HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
                httpsConfig.addCustomizer(new SecureRequestCustomizer());

                // SSL Connector
                ServerConnector sslConnector = new ServerConnector(server,
                        new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.toString()),
                        new HttpConnectionFactory(httpsConfig));
                sslConnector.setPort(securePort);
                server.addConnector(sslConnector);

            } catch (IOException e) {
                throw new DnException("Cannot configure SSL store", e);
            }

        }


        ConnectHandler proxy = new ConnectHandler();
        var otherVirtualHosts = DnConfigUtil.getConfigString(cxt, "virtual.otherHosts", null,
                "Other virtual hosts supported by this server.");
        var otherProxyTo = DnConfigUtil.getConfigString(cxt, "virtual.proxyTo", null,
                "The HTTP address where other virtual host traffic is sent.");
        List<String> vHosts = otherVirtualHosts != null ? StrUtil.splitString(otherVirtualHosts, ",") : mList();
        DnHandler dnHandler = new DnHandler(vHosts);
        dnHandler.setHandler(proxy);

        server.setHandler(dnHandler);
        if (otherProxyTo != null) {
            // Setup proxy servlet
            ServletContextHandler context = new ServletContextHandler(dnHandler, "/", ServletContextHandler.SESSIONS);
            ServletHolder proxyServlet = new ServletHolder(ProxyServlet.Transparent.class);
            proxyServlet.setInitParameter("proxyTo", otherProxyTo);
            proxyServlet.setInitParameter("prefix", "/");
            context.addServlet(proxyServlet, "/*");
        }


        try {
            server.start();
            LogServlet.log.debug(cxt, String.format("Started server at %s:%d on hostname %s.",
                    nodeId.nodeIpAddress, nodeId.port, nodeId.hostname));
            try {
                server.join();
            } catch (InterruptedException ignore) {}
        } catch (Exception e) {
            try {
                server.stop();
            } catch (Exception ignore) {}
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
