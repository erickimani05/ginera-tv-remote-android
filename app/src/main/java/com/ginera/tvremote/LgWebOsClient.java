package com.ginera.tvremote;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class LgWebOsClient {
    public interface Listener {
        void onStatus(String status);
        void onPaired();
    }

    private static final String PREFS = "lg_webos";
    private static final String CLIENT_KEY = "client_key";

    private final OkHttpClient standardHttp = new OkHttpClient();
    private final OkHttpClient localTvSecureHttp;
    private final SharedPreferences prefs;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Listener listener;
    private WebSocket commandSocket;
    private WebSocket pointerSocket;
    private String currentIp;

    public LgWebOsClient(Context context, Listener listener) {
        this.listener = listener;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.localTvSecureHttp = createLocalTvSecureClient();
    }

    public void connect(String ipAddress) {
        close();
        currentIp = ipAddress.trim();
        listener.onStatus("Connecting to LG TV on port 3000…");
        connectEndpoint(false);
    }

    private void connectEndpoint(boolean secure) {
        String url = (secure ? "wss://" : "ws://") + currentIp + (secure ? ":3001" : ":3000");
        Request request = new Request.Builder().url(url).build();
        OkHttpClient client = secure ? localTvSecureHttp : standardHttp;
        commandSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                listener.onStatus("Approve the pairing request on the LG TV");
                sendRegistration(webSocket);
            }

            @Override public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                handleMessage(text);
            }

            @Override public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                if (!secure && commandSocket == webSocket) {
                    listener.onStatus("Port 3000 unavailable; trying secure LG port 3001…");
                    connectEndpoint(true);
                } else if (secure && commandSocket == webSocket) {
                    listener.onStatus("LG connection failed on ports 3000 and 3001: " + safeMessage(t));
                }
            }

            @Override public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                if (commandSocket == webSocket) listener.onStatus("LG disconnected");
            }
        });
    }

    private OkHttpClient createLocalTvSecureClient() {
        try {
            X509TrustManager localTrust = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[]{localTrust}, new SecureRandom());
            return new OkHttpClient.Builder()
                    .sslSocketFactory(ssl.getSocketFactory(), localTrust)
                    .hostnameVerifier((hostname, session) -> hostname.equals(currentIp))
                    .build();
        } catch (Exception e) {
            return standardHttp;
        }
    }

    private String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private void sendRegistration(WebSocket socket) {
        try {
            JSONArray permissions = new JSONArray()
                    .put("LAUNCH").put("LAUNCH_WEBAPP").put("APP_TO_APP")
                    .put("CONTROL_AUDIO").put("CONTROL_POWER").put("READ_INSTALLED_APPS")
                    .put("CONTROL_DISPLAY").put("CONTROL_INPUT_JOYSTICK")
                    .put("CONTROL_INPUT_MEDIA_RECORDING").put("CONTROL_INPUT_MEDIA_PLAYBACK")
                    .put("CONTROL_INPUT_TV").put("READ_INPUT_DEVICE_LIST")
                    .put("READ_NETWORK_STATE").put("READ_TV_CHANNEL_LIST")
                    .put("WRITE_NOTIFICATION_TOAST").put("READ_POWER_STATE");

            JSONObject manifest = new JSONObject()
                    .put("manifestVersion", 1)
                    .put("appVersion", "1.0.1")
                    .put("permissions", permissions)
                    .put("signed", new JSONObject()
                            .put("appId", "com.ginera.tvremote")
                            .put("vendorId", "com.ginera")
                            .put("localizedAppNames", new JSONObject().put("", "Ginera TV Remote"))
                            .put("localizedVendorNames", new JSONObject().put("", "Ginera"))
                            .put("permissions", permissions));

            JSONObject payload = new JSONObject()
                    .put("pairingType", "PROMPT")
                    .put("forcePairing", false)
                    .put("manifest", manifest);

            String key = prefs.getString(CLIENT_KEY, "");
            if (!key.isEmpty()) payload.put("client-key", key);

            socket.send(new JSONObject()
                    .put("id", "register_0")
                    .put("type", "register")
                    .put("payload", payload).toString());
        } catch (Exception e) {
            listener.onStatus("Pairing setup error: " + e.getMessage());
        }
    }

    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type");
            if ("registered".equals(type)) {
                JSONObject payload = json.optJSONObject("payload");
                String key = payload == null ? "" : payload.optString("client-key");
                if (!key.isEmpty()) prefs.edit().putString(CLIENT_KEY, key).apply();
                listener.onStatus("LG connected");
                listener.onPaired();
                openPointerSocket();
                return;
            }
            if ("error".equals(type)) {
                listener.onStatus("LG error: " + json.optString("error"));
                return;
            }
            if ("pointer_socket".equals(json.optString("id"))) {
                JSONObject payload = json.optJSONObject("payload");
                if (payload != null) connectPointer(payload.optString("socketPath"));
            }
        } catch (Exception ignored) {
            listener.onStatus("Received an unreadable TV response");
        }
    }

    public void request(String uri) {
        request(uri, null);
    }

    public void request(String uri, JSONObject payload) {
        WebSocket socket = commandSocket;
        if (socket == null) {
            listener.onStatus("Connect to the LG TV first");
            return;
        }
        try {
            JSONObject message = new JSONObject()
                    .put("id", "request_" + nextId.getAndIncrement())
                    .put("type", "request")
                    .put("uri", uri);
            if (payload != null) message.put("payload", payload);
            socket.send(message.toString());
        } catch (Exception e) {
            listener.onStatus("Command error: " + e.getMessage());
        }
    }

    private void openPointerSocket() {
        WebSocket socket = commandSocket;
        if (socket == null) return;
        try {
            socket.send(new JSONObject()
                    .put("id", "pointer_socket")
                    .put("type", "request")
                    .put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
                    .toString());
        } catch (Exception e) {
            listener.onStatus("Touchpad unavailable: " + e.getMessage());
        }
    }

    private void connectPointer(String url) {
        if (url == null || url.isEmpty()) return;
        OkHttpClient client = url.startsWith("wss://") ? localTvSecureHttp : standardHttp;
        pointerSocket = client.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
            @Override public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                listener.onStatus("LG connected • touchpad ready");
            }
        });
    }

    public void button(String name) {
        WebSocket socket = pointerSocket;
        if (socket == null) {
            listener.onStatus("Touchpad is not ready yet");
            return;
        }
        socket.send("type:button\nname:" + name + "\n\n");
    }

    public void move(int dx, int dy) {
        WebSocket socket = pointerSocket;
        if (socket != null) socket.send("type:move\ndx:" + dx + "\ndy:" + dy + "\ndown:0\n\n");
    }

    public void click() {
        WebSocket socket = pointerSocket;
        if (socket != null) socket.send("type:click\n\n");
    }

    public void close() {
        WebSocket oldPointer = pointerSocket;
        WebSocket oldCommand = commandSocket;
        pointerSocket = null;
        commandSocket = null;
        if (oldPointer != null) oldPointer.close(1000, "Closing");
        if (oldCommand != null) oldCommand.close(1000, "Closing");
    }
}
