package com.veniilocal;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/** Local protocol: UDP discovery, TCP auth(67), status(888), start(1020). */
public final class VeniiClient {
    private static final String HOST = "192.168.78.1";
    private static final int PORT = 9000;
    private static final int AUTH = 67, STATUS = 888, START = 1020;
    private static final String[] DISCOVERY = {"n1_find", "veniibot_find"};

    private VeniiClient() {}

    private static final class Discovery {
        final String auth, mac;
        Discovery(String auth, String mac) { this.auth = auth; this.mac = mac; }
    }

    /** Credentials exist in-memory only; no logs, on-disk storage or UI exposure. */
    private static Discovery discover() throws Exception {
        InetAddress target = InetAddress.getByName(HOST);
        DatagramSocket udp;
        try { udp = new DatagramSocket(PORT); }
        catch (IOException busy) { udp = new DatagramSocket(); }
        try {
            udp.setSoTimeout(500);
            for (String probe : DISCOVERY) {
                byte[] data = probe.getBytes(StandardCharsets.UTF_8);
                udp.send(new DatagramPacket(data, data.length, target, PORT));
            }
            byte[] buff = new byte[4096];
            long until = System.nanoTime() + 8_000_000_000L;
            while (System.nanoTime() < until) {
                DatagramPacket rx = new DatagramPacket(buff, buff.length);
                try { udp.receive(rx); }
                catch (SocketTimeoutException timeout) { continue; }
                if (!target.equals(rx.getAddress())) continue;
                try {
                    JSONObject reply = new JSONObject(new String(rx.getData(), rx.getOffset(), rx.getLength(), StandardCharsets.UTF_8));
                    String auth = reply.optString("auth", "");
                    String mac = reply.optString("mac", "");
                    if (!auth.isEmpty() && !mac.isEmpty()) return new Discovery(auth, mac);
                } catch (Exception ignored) { /* Discovery packet not meant for us. */ }
            }
        } finally { udp.close(); }
        throw new IOException("Robot non détecté : connectez le téléphone au Wi-Fi VENII.");
    }

    private static void send(Socket socket, String json) throws Exception {
        socket.getOutputStream().write(VeniiFrameCodec.encode(json));
        socket.getOutputStream().flush();
    }

    private static Object receive(Socket socket, int timeoutMs) throws Exception {
        socket.setSoTimeout(timeoutMs);
        return new JSONTokener(VeniiFrameCodec.decodeFrame(socket.getInputStream())).nextValue();
    }

    private static Map<Integer, Object> extract(Object obj) {
        Map<Integer, Object> data = new HashMap<>();
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            for (int i = 0; i < array.length(); ++i) addIfCode(array.optJSONObject(i), data);
        } else if (obj instanceof JSONObject) addIfCode((JSONObject) obj, data);
        return data;
    }

    private static void addIfCode(JSONObject obj, Map<Integer, Object> data) {
        if (obj != null && obj.opt("id") instanceof Number) {
            int id = obj.optInt("id", -1);
            if (id >= 0) data.put(id, obj.opt("content"));
        }
    }

    private static String envelope(int code, String mac) throws Exception {
        JSONObject item = new JSONObject();
        item.put("from", "android");
        item.put("to_name", mac);
        item.put("from_name", "local");
        item.put("type", "cmd");
        item.put("id", code);
        JSONArray array = new JSONArray();
        array.put(item);
        return array.toString();
    }

    private static final class Session implements AutoCloseable {
        final Socket socket;
        final String mac;
        Session(Socket socket, String mac) { this.socket = socket; this.mac = mac; }
        @Override public void close() throws IOException { socket.close(); }
    }

    private static Session openSession() throws Exception {
        Discovery discovery = discover();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(HOST, PORT), 5000);
            JSONObject authMessage = new JSONObject();
            authMessage.put("id", AUTH);
            authMessage.put("content", discovery.auth);
            send(socket, authMessage.toString());
            for (int i = 0; i < 3; i++) {
                Object reply = receive(socket, 8000);
                if (extract(reply).containsKey(AUTH)) return new Session(socket, discovery.mac);
            }
            throw new IOException("Aucune réponse d'authentification reconnue (67).");
        } catch (Exception err) {
            socket.close();
            throw err;
        }
    }

    private static Map<Integer, Object> requestStatus(Session session) throws Exception {
        send(session.socket, envelope(STATUS, session.mac));
        for (int i = 0; i < 3; i++) {
            Map<Integer, Object> items = extract(receive(session.socket, 9000));
            if (items.containsKey(STATUS)) return items;
        }
        throw new IOException("Aucune réponse d'état reconnue (888).");
    }

    public static Status readStatus() throws Exception {
        try (Session session = openSession()) {
            return new Status(requestStatus(session));
        }
    }

    /** Caller must require physical checks and user confirmation BEFORE invoking. */
    public static StartResult startOnce() throws Exception {
        try (Session session = openSession()) {
            Status before = new Status(requestStatus(session));
            send(session.socket, envelope(START, session.mac));
            // 1020 may be executed without separate ACK. Never claim success from send alone.
            Status after = null;
            try {
                Thread.sleep(1600);
                after = new Status(requestStatus(session));
            } catch (InterruptedException exc) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) { /* command may close socket or produce no subsequent state */ }
            return new StartResult(before, after);
        }
    }

    public static final class StartResult {
        public final Status before, after;
        StartResult(Status before, Status after) { this.before = before; this.after = after; }
    }

    public static final class Status {
        public final String generalState, powerReport, suction, volume, error;
        public final int messageCount;
        Status(Map<Integer, Object> payload) {
            messageCount = payload.size();
            generalState = format(payload.get(888));
            powerReport = format(payload.get(60));
            suction = format(payload.get(1300));
            volume = format(payload.get(1502));
            error = format(payload.get(70));
        }
        private static String format(Object value) {
            if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
            return "—"; // Deliberately never expose raw auth/MAC/other payloads.
        }
    }
}
