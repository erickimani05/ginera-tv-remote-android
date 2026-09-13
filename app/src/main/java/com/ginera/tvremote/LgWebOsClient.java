package com.ginera.tvremote;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

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

    private final OkHttpClient http = new OkHttpClient();
    private final SharedPreferences prefs;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Listener listener;
    private WebSocket commandSocket;
    private WebSocket pointerSocket;

    public LgWebOsClient(Context context, Listener listener) {
        this.listener = listener;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void connect(String ipAddress) {
        close();
        listener.onStatus("Connecting to LG TV…");
        Request request = new Request.Builder().url("ws://" + ipAddress.trim() + ":3000").build();
        commandSocket = http.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                listener.onStatus("Approve the pairing request on the LG TV");
                sendRegistration(webSocket);
            }

            @Override public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                handleMessage(text);
            }

            @Override public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                listener.onStatus("Connection failed: " + t.getMessage());
            }

            @Override public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                listener.onStatus("LG disconnected");
            }
        });
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
                    .put("appVersion", "1.0.0")
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
                String key = json.optJSONObject("payload") == null ? "" :
                        json.optJSONObject("payload").optString("client-key");
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
        pointerSocket = http.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
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
        if (pointerSocket != null) pointerSocket.close(1000, "Closing");
        if (commandSocket != null) commandSocket.close(1000, "Closing");
        pointerSocket = null;
        commandSocket = null;
    }
}
