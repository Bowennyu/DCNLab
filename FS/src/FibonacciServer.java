import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FibonacciServer {

    public static void main(String[] args) throws Exception {
        int port = 9090;
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/register", FibonacciServer::handleRegister);
        server.createContext("/fibonacci", FibonacciServer::handleFibonacci);

        server.setExecutor(null);
        System.out.println("FS listening on http://0.0.0.0:" + port);
        server.start();
    }

    // ---- HTTP handlers ----

    private static void handleRegister(HttpExchange ex) throws IOException {
        if (!"PUT".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "Method Not Allowed");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        Map<String, String> json = parseFlatJson(body);

        String hostname = json.get("hostname");
        String ip = json.get("ip");
        String asIp = json.get("as_ip");
        String asPortStr = json.get("as_port");

        if (hostname == null || ip == null || asIp == null || asPortStr == null) {
            send(ex, 400, "Missing fields in JSON. Need hostname, ip, as_ip, as_port");
            return;
        }

        int asPort;
        try {
            asPort = Integer.parseInt(asPortStr.trim());
        } catch (Exception e) {
            send(ex, 400, "as_port must be an integer");
            return;
        }

        // Normalize hostname for consistency
        hostname = hostname.trim().toLowerCase();

        // UDP REGISTER must be acknowledged by AS
        String udpResp;
        try {
            udpResp = udpRequest(asIp.trim(), asPort, "REGISTER " + hostname + " " + ip.trim(), 1500);
        } catch (Exception e) {
            send(ex, 500, "AS registration failed (UDP error): " + e.getMessage());
            return;
        }

        if (!"OK".equalsIgnoreCase(udpResp.trim())) {
            send(ex, 500, "AS registration failed. AS response: " + udpResp);
            return;
        }

        send(ex, 201, "Registered: " + hostname + " -> " + ip.trim());
    }

    private static void handleFibonacci(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());

        String hostname = q.get("hostname");
        String numberStr = q.get("number");
        String asIp = q.get("as_ip");
        String asPortStr = q.get("as_port");

        if (hostname == null || numberStr == null || asIp == null || asPortStr == null) {
            send(ex, 400, "Missing query params. Need hostname, number, as_ip, as_port");
            return;
        }

        int n;
        try {
            n = Integer.parseInt(numberStr.trim());
        } catch (Exception e) {
            send(ex, 400, "number must be an integer");
            return;
        }
        if (n < 0 || n > 45) {
            send(ex, 400, "number must be between 0 and 45");
            return;
        }

        int asPort;
        try {
            asPort = Integer.parseInt(asPortStr.trim());
        } catch (Exception e) {
            send(ex, 400, "as_port must be an integer");
            return;
        }

        hostname = hostname.trim().toLowerCase();

        String udpResp;
        try {
            udpResp = udpRequest(asIp.trim(), asPort, "QUERY " + hostname, 1500);
        } catch (Exception e) {
            send(ex, 500, "AS query failed (UDP error): " + e.getMessage());
            return;
        }

        if (udpResp.startsWith("FOUND ")) {
            // In this lab, ip is not actually used for computation (FS computes locally),
            // but we validate that hostname exists in AS.
            int result = fib(n);
            send(ex, 200, String.valueOf(result));
            return;
        }

        if ("NOTFOUND".equalsIgnoreCase(udpResp.trim())) {
            send(ex, 404, "Hostname not found in AS. AS response: NOTFOUND");
            return;
        }

        send(ex, 500, "Unexpected AS response: " + udpResp);
    }

    // ---- UDP helper ----

    private static String udpRequest(String host, int port, String msg, int timeoutMs) throws Exception {
        System.out.println("FS UDP -> AS host=" + host + " port=" + port + " msg=[" + msg + "]");

        DatagramSocket sock = new DatagramSocket();
        sock.setSoTimeout(timeoutMs);

        byte[] out = msg.getBytes(StandardCharsets.UTF_8);
        InetAddress addr = InetAddress.getByName(host);
        DatagramPacket packet = new DatagramPacket(out, out.length, addr, port);
        sock.send(packet);

        byte[] buf = new byte[2048];
        DatagramPacket resp = new DatagramPacket(buf, buf.length);
        sock.receive(resp);

        String respStr = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8).trim();
        System.out.println("FS UDP <- AS resp=[" + respStr + "]");
        sock.close();
        return respStr;
    }

    // ---- utilities ----

    private static int fib(int n) {
        if (n <= 1) return n;
        int a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            int c = a + b;
            a = b;
            b = c;
        }
        return b;
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Very small flat JSON parser for {"k":"v",...} (no nesting)
    private static Map<String, String> parseFlatJson(String s) {
        Map<String, String> m = new HashMap<>();
        s = s.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        // split by commas not inside quotes (assume simple input)
        String[] pairs = s.split(",");
        for (String p : pairs) {
            String[] kv = p.split(":", 2);
            if (kv.length != 2) continue;
            String k = strip(kv[0]);
            String v = strip(kv[1]);
            m.put(k, v);
        }
        return m;
    }

    private static String strip(String x) {
        x = x.trim();
        if (x.startsWith("\"")) x = x.substring(1);
        if (x.endsWith("\"")) x = x.substring(0, x.length() - 1);
        return x;
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null || q.isEmpty()) return m;
        for (String part : q.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) m.put(urlDecode(kv[0]), urlDecode(kv[1]));
        }
        return m;
    }

    private static String urlDecode(String s) {
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }
}
