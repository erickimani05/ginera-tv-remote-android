package com.ginera.tvremote;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements LgWebOsClient.Listener {
    private LgWebOsClient lg;
    private TextView status;
    private EditText ipField;
    private EditText macField;
    private boolean muted;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        lg = new LgWebOsClient(this, this);
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(17, 24, 39));
        LinearLayout page = column();
        page.setPadding(dp(18), dp(22), dp(18), dp(32));
        scroll.addView(page);

        TextView title = text("GINERA TV REMOTE", 25, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        page.addView(title);
        TextView subtitle = text("One remote. Every screen.", 14, Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        page.addView(subtitle);

        page.addView(section("LG webOS TV"));
        ipField = input("LG IP address");
        ipField.setInputType(InputType.TYPE_CLASS_PHONE);
        ipField.setText(getPreferences(MODE_PRIVATE).getString("lg_ip", ""));
        page.addView(ipField);

        macField = input("LG MAC address (for Power On)");
        macField.setText(getPreferences(MODE_PRIVATE).getString("lg_mac", ""));
        page.addView(macField);

        status = text("Enter the TV details, then pair.", 14, Color.LTGRAY);
        status.setPadding(0, dp(8), 0, dp(8));
        page.addView(status);

        page.addView(button("PAIR / CONNECT LG", () -> {
            String ip = ipField.getText().toString().trim();
            String mac = macField.getText().toString().trim();
            if (ip.isEmpty()) {
                status.setText("Enter the LG IP address shown in Network Settings");
                return;
            }
            getPreferences(MODE_PRIVATE).edit().putString("lg_ip", ip).putString("lg_mac", mac).apply();
            lg.connect(ip);
        }));

        page.addView(section("Power & sound"));
        page.addView(row(
                button("POWER ON", this::wakeLg),
                button("POWER OFF", () -> lg.request("ssap://system/turnOff"))
        ));
        page.addView(row(
                button("VOL −", () -> lg.request("ssap://audio/volumeDown")),
                button("MUTE", this::toggleMute),
                button("VOL +", () -> lg.request("ssap://audio/volumeUp"))
        ));

        page.addView(section("Navigation"));
        page.addView(centered(button("▲", () -> lg.button("UP"))));
        page.addView(row(
                button("◀", () -> lg.button("LEFT")),
                button("OK", () -> lg.button("ENTER")),
                button("▶", () -> lg.button("RIGHT"))
        ));
        page.addView(centered(button("▼", () -> lg.button("DOWN"))));
        page.addView(row(
                button("HOME", () -> lg.button("HOME")),
                button("BACK", () -> lg.button("BACK"))
        ));

        page.addView(section("Touchpad"));
        TouchpadView touchpad = new TouchpadView(this);
        touchpad.setListener(new TouchpadView.Listener() {
            @Override public void onMove(int dx, int dy) { lg.move(dx, dy); }
            @Override public void onClick() { lg.click(); }
        });
        page.addView(touchpad, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(180)));

        page.addView(section("Channels"));
        page.addView(row(
                button("CH −", () -> lg.request("ssap://tv/channelDown")),
                button("CH +", () -> lg.request("ssap://tv/channelUp"))
        ));
        int number = 1;
        for (int r = 0; r < 3; r++) {
            Button[] keys = new Button[3];
            for (int c = 0; c < 3; c++) {
                final String digit = String.valueOf(number++);
                keys[c] = button(digit, () -> openChannel(digit));
            }
            page.addView(row(keys));
        }
        page.addView(centered(button("0", () -> openChannel("0"))));

        page.addView(section("Apps & inputs"));
        page.addView(row(
                button("HDMI 1", () -> switchInput("HDMI_1")),
                button("HDMI 2", () -> switchInput("HDMI_2"))
        ));
        page.addView(row(
                button("YOUTUBE", () -> launch("youtube.leanback.v4")),
                button("NETFLIX", () -> launch("netflix"))
        ));

        page.addView(section("Keyboard"));
        EditText keyboard = input("Type text for the TV");
        page.addView(keyboard);
        page.addView(button("SEND TEXT", () -> {
            try {
                lg.request("ssap://com.webos.service.ime/insertText",
                        new JSONObject().put("text", keyboard.getText().toString()).put("replace", 0));
            } catch (Exception ignored) {}
        }));

        page.addView(section("Haier Android TV"));
        TextView haier = text(
                "Haier companion is queued for Phase 2. The TV and phone must first be on the same Wi-Fi, or the two routers must be linked.",
                14, Color.LTGRAY);
        haier.setPadding(dp(12), dp(12), dp(12), dp(18));
        page.addView(haier);

        return scroll;
    }

    private void wakeLg() {
        String mac = macField.getText().toString().trim();
        if (mac.isEmpty()) {
            status.setText("Enter the LG MAC address to use Power On");
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                WolSender.wake(mac);
                runOnUiThread(() -> status.setText("LG wake signal sent"));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Power On failed: " + e.getMessage()));
            }
        });
    }

    private void toggleMute() {
        muted = !muted;
        try {
            lg.request("ssap://audio/setMute", new JSONObject().put("mute", muted));
        } catch (Exception ignored) {}
    }

    private void openChannel(String channel) {
        try {
            lg.request("ssap://tv/openChannel", new JSONObject().put("channelNumber", channel));
        } catch (Exception ignored) {}
    }

    private void switchInput(String id) {
        try {
            lg.request("ssap://tv/switchInput", new JSONObject().put("inputId", id));
        } catch (Exception ignored) {}
    }

    private void launch(String appId) {
        try {
            lg.request("ssap://system.launcher/launch", new JSONObject().put("id", appId));
        } catch (Exception ignored) {}
    }

    @Override public void onStatus(String message) {
        runOnUiThread(() -> status.setText(message));
    }

    @Override public void onPaired() {
        runOnUiThread(() -> Toast.makeText(this, "LG pairing saved", Toast.LENGTH_SHORT).show());
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView section(String value) {
        TextView view = text(value, 18, Color.WHITE);
        view.setPadding(0, dp(24), 0, dp(8));
        return view;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private EditText input(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.GRAY);
        field.setSingleLine(true);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        return field;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private LinearLayout row(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button button : buttons) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(button, params);
        }
        return row;
    }

    private LinearLayout centered(Button button) {
        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(Gravity.CENTER);
        holder.addView(button, new LinearLayout.LayoutParams(dp(130),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        lg.close();
        super.onDestroy();
    }
}
