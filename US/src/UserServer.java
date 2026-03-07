import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class UserServer {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/fibonacci", UserServer::handleFibonacci);
        server.setExecutor(null);
        System.out.println("US listening on http://0.0.0.0:" + port);
        server.start();
    }

    private static void handleFibonacci(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());

        String hostname = q.get("hostname");
        String fsPortStr = q.get("fs_port");
        String numberStr = q.get("number");
        String asIp = q.get("as_ip");
        String asPortStr = q.get("as_port");

        if (hostname == null || fsPortStr == null || numberStr == null || asIp == null || asPortStr == null) {
            send(ex, 400, "Missing query params. Need hostname, fs_port, number, as_ip, as_port");
            return;
        }

        int fsPort, asPort, n;
        try { fsPort = Integer.parseInt(fsPortStr.trim()); }
        catch (Exception e) { send(ex, 400, "fs_port must be an integer"); return; }

        try { asPort = Integer.parseInt(asPortStr.trim()); }
        catch (Exception e) { send(ex, 400, "as_port must be an integer"); return; }

        try { n = Integer.parseInt(numberStr.trim()); }
        catch (Exception e) { send(ex, 400, "number must be an integer"); return; }

        if (n < 0 || n > 45) {
            send(ex, 400, "number must be between 0 and 45");
            return;
        }

        hostname = hostname.trim().toLowerCase();
        asIp = asIp.trim();

        // 1) Ask AS via UDP: QUERY <hostname>
        String asResp;
        try {
            asResp = udpRequest(asIp, asPort, "QUERY " + hostname, 1500);
        } catch (Exception e) {
            send(ex, 500, "AS query failed (UDP error): " + e.getMessage());
            return;
        }

        if ("NOTFOUND".equalsIgnoreCase(asResp.trim())) {
            send(ex, 404, "Hostname not found in AS. AS response: NOTFOUND");
            return;
        }

        if (!asResp.startsWith("FOUND ")) {
            send(ex, 500, "Unexpected AS response: " + asResp);
            return;
        }

        String fsHost = asResp.substring("FOUND ".length()).trim(); // e.g., "fs"
        if (fsHost.isEmpty()) {
            send(ex, 500, "AS returned FOUND but empty FS host");
            return;
        }

        // 2) Call FS via HTTP using FS's expected params:
        // /fibonacci?hostname=...&number=...&as_ip=...&as_port=...
        String fsUrl = "http://" + fsHost + ":" + fsPort
                + "/fibonacci?hostname=" + urlEncode(hostname)
                + "&number=" + n
                + "&as_ip=" + urlEncode(asIp)
                + "&as_port=" + asPort;

        int fsCode;
        String fsBody;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(fsUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(3000);

            fsCode = conn.getResponseCode();
            InputStream is = (fsCode >= 200 && fsCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            fsBody = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            conn.disconnect();
        } catch (Exception e) {
            send(ex, 500, "Failed to contact FS: " + e.getMessage());
            return;
        }

        if (fsCode != 200) {
            send(ex, 500, "FS returned " + fsCode + ": " + fsBody);
            return;
        }

        send(ex, 200, fsBody);
    }

    private static String udpRequest(String host, int port, String msg, int timeoutMs) throws Exception {
        System.out.println("US UDP -> AS host=" + host + " port=" + port + " msg=[" + msg + "]");
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
        System.out.println("US UDP <- AS resp=[" + respStr + "]");
        sock.close();
        return respStr;
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
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

    private static String urlEncode(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }
}
