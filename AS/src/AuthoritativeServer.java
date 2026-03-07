import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthoritativeServer {

    // hostname -> ip
    private static final Map<String, String> table = new ConcurrentHashMap<>();

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java AuthoritativeServer <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);

        // IMPORTANT: listen on all interfaces inside container
        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("AS listening on UDP port " + port);

        byte[] buf = new byte[2048];

        while (true) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);

            String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
            System.out.println("AS received UDP from " + p.getAddress().getHostAddress() + ":" + p.getPort() + " => [" + msg + "]");

            String resp = handle(msg);
            byte[] out = resp.getBytes(StandardCharsets.UTF_8);

            DatagramPacket reply = new DatagramPacket(out, out.length, p.getAddress(), p.getPort());
            socket.send(reply);

            System.out.println("AS replied => [" + resp + "]");
        }
    }

    private static String handle(String msg) {
        // Protocol:
        // REGISTER <hostname> <ip>
        // QUERY <hostname>
        String[] parts = msg.split("\\s+");
        if (parts.length == 0) return "ERROR";

        String cmd = parts[0].toUpperCase();

        if ("REGISTER".equals(cmd)) {
            if (parts.length < 3) return "ERROR";
            String hostname = norm(parts[1]);
            String ip = parts[2].trim(); // keep as-is (e.g., "fs" container name)
            table.put(hostname, ip);
            System.out.println("AS stored: " + hostname + " -> " + ip);
            return "OK";
        }

        if ("QUERY".equals(cmd)) {
            if (parts.length < 2) return "ERROR";
            String hostname = norm(parts[1]);
            String ip = table.get(hostname);
            if (ip == null) return "NOTFOUND";
            return "FOUND " + ip;
        }

        return "ERROR";
    }
}
