package com.mywebsite;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class StaticServer {
    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        int port = PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("端口参数无效，使用默认端口 " + PORT);
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("========================================");
        System.out.println("  个人网站已启动！");
        System.out.println("  访问地址: http://localhost:" + port);
        System.out.println("  首页: http://localhost:" + port + "/index.html");
        System.out.println("========================================");
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            String resourcePath = "static" + path;
            InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);

            if (is == null) {
                String notFound = "<html><body><h1>404 - 页面不存在</h1><p>请求路径: " + path + "</p></body></html>";
                exchange.sendResponseHeaders(404, notFound.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(notFound.getBytes(StandardCharsets.UTF_8));
                os.close();
                return;
            }

            String contentType = getContentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            is.close();
            os.close();
        }

        private String getContentType(String path) {
            if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (path.endsWith(".css")) return "text/css; charset=UTF-8";
            if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (path.endsWith(".json")) return "application/json; charset=UTF-8";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".gif")) return "image/gif";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".ico")) return "image/x-icon";
            if (path.endsWith(".woff")) return "font/woff";
            if (path.endsWith(".woff2")) return "font/woff2";
            if (path.endsWith(".ttf")) return "font/ttf";
            return "application/octet-stream";
        }
    }
}
