package br.com.harm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class MainActivity extends Activity {
    public static final String ACTION_COMMAND = "br.com.harm.COMMAND";
    public static final String PREFS = "remote_ha";
    public static final String KEY_TOKEN = "api_token";
    public static final String KEY_NVR_HOST = "nvr_host";
    public static final String KEY_NVR_USER = "nvr_user";
    public static final String KEY_NVR_PASSWORD = "nvr_password";
    public static final String KEY_PENDING_CAMERA = "pending_camera";
    public static final String KEY_PENDING_TIMEOUT = "pending_timeout";
    public static final String KEY_PENDING_MEDIA = "pending_media";
    public static final String KEY_CAMERAS = "selected_cameras";
    public static final String KEY_CAMERA_NAMES = "camera_names";
    public static final int API_PORT = 8765;

    private SharedPreferences prefs;
    private VLCVideoLayout playerView;
    private LibVLC libVLC;
    private MediaPlayer player;
    private TextView idleView;
    private Handler handler;
    private Runnable stopTask;
    private Runnable watchdog;
    private String currentMediaUrl;
    private long lastProgressAt;
    private BroadcastReceiver receiver;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        handler = new Handler();
        ArrayList<String> vlcOptions = new ArrayList<>();
        vlcOptions.add("--rtsp-tcp");
        vlcOptions.add("--network-caching=700");
        vlcOptions.add("--clock-jitter=0");
        libVLC = new LibVLC(this, vlcOptions);
        if (prefs.getString(KEY_TOKEN, "").isEmpty())
            prefs.edit().putString(KEY_TOKEN, UUID.randomUUID().toString().replace("-", "")).apply();
        immersive();
        createScreen();
        registerCommands();
        watchdog = new Runnable() {
            @Override public void run() {
                if (currentMediaUrl != null && SystemClock.elapsedRealtime() - lastProgressAt > 10000) restartStream();
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(watchdog, 3000);
        startService(new Intent(this, RemoteControlService.class));
        showIdle();
        if (prefs.getString(KEY_NVR_HOST, "").isEmpty()) showSettings(false);
    }

    private void immersive() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void createScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        playerView = new VLCVideoLayout(this);
        playerView.setBackgroundColor(Color.BLACK);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        idleView = new TextView(this);
        idleView.setText("HARM"); idleView.setTextSize(18); idleView.setTextColor(0xff303030);
        idleView.setGravity(Gravity.CENTER); idleView.setBackgroundColor(Color.BLACK);
        root.addView(idleView, new FrameLayout.LayoutParams(-1, -1));
        Button settings = new Button(this);
        settings.setText("⚙"); settings.setTextColor(Color.WHITE); settings.setTextSize(16);
        settings.setBackgroundColor(0x22000000); settings.setOnClickListener(v -> showSettings(true));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(58, 58, Gravity.TOP | Gravity.RIGHT);
        bp.setMargins(0, 6, 6, 0); root.addView(settings, bp);
        setContentView(root);
    }

    private void registerCommands() {
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if ("camera".equals(i.getStringExtra("command")))
                { prefs.edit().remove(KEY_PENDING_CAMERA).remove(KEY_PENDING_TIMEOUT).apply();
                    playCamera(i.getIntExtra("camera", 1), i.getIntExtra("timeout", 0)); }
                else if ("media".equals(i.getStringExtra("command")))
                { prefs.edit().remove(KEY_PENDING_MEDIA).remove(KEY_PENDING_TIMEOUT).apply();
                    playMedia(i.getStringExtra("url"), i.getIntExtra("timeout", 0)); }
                else if ("stop".equals(i.getStringExtra("command"))) showIdle();
            }
        };
        registerReceiver(receiver, new IntentFilter(ACTION_COMMAND));
    }

    @Override protected void onResume() {
        super.onResume(); immersive();
        int camera = prefs.getInt(KEY_PENDING_CAMERA, 0);
        String media = prefs.getString(KEY_PENDING_MEDIA, "");
        if (!media.isEmpty()) {
            int timeout = prefs.getInt(KEY_PENDING_TIMEOUT, 0);
            prefs.edit().remove(KEY_PENDING_MEDIA).remove(KEY_PENDING_TIMEOUT).apply();
            playMedia(media, timeout); return;
        }
        if (camera > 0) {
            int timeout = prefs.getInt(KEY_PENDING_TIMEOUT, 0);
            prefs.edit().remove(KEY_PENDING_CAMERA).remove(KEY_PENDING_TIMEOUT).apply();
            playCamera(camera, timeout);
        }
    }

    private void playCamera(int channel, int timeout) {
        String host = prefs.getString(KEY_NVR_HOST, "");
        String user = prefs.getString(KEY_NVR_USER, "");
        String password = prefs.getString(KEY_NVR_PASSWORD, "");
        if (host.isEmpty() || user.isEmpty()) { Toast.makeText(this, "Configure the NVR", Toast.LENGTH_LONG).show(); showSettings(true); return; }
        showIdle(); idleView.setVisibility(View.GONE);
        String stream = "rtsp://" + Uri.encode(user) + ":" + Uri.encode(password) + "@" + host
                + ":554/cam/realmonitor?channel=" + channel + "&subtype=0";
        startVlc(stream);
        if (timeout > 0) { stopTask = this::showIdle; handler.postDelayed(stopTask, timeout * 1000L); }
    }

    private void playMedia(String url, int timeout) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtsp://"))) return;
        showIdle(); idleView.setVisibility(View.GONE);
        startVlc(url);
        if (timeout > 0) { stopTask = this::showIdle; handler.postDelayed(stopTask, timeout * 1000L); }
    }

    private void showIdle() {
        if (stopTask != null) handler.removeCallbacks(stopTask); stopTask = null;
        currentMediaUrl = null;
        stopVlc();
        if (idleView != null) idleView.setVisibility(View.VISIBLE);
    }

    private void startVlc(String url) {
        currentMediaUrl = url;
        lastProgressAt = SystemClock.elapsedRealtime();
        player = new MediaPlayer(libVLC);
        player.setEventListener(event -> {
            if (event.type == MediaPlayer.Event.Playing || event.type == MediaPlayer.Event.TimeChanged
                    || event.type == MediaPlayer.Event.PositionChanged)
                lastProgressAt = SystemClock.elapsedRealtime();
            else if (event.type == MediaPlayer.Event.EncounteredError)
                handler.post(this::restartStream);
        });
        player.attachViews(playerView, null, false, false);
        Media media = new Media(libVLC, Uri.parse(url));
        media.setHWDecoderEnabled(false, false);
        media.addOption(":network-caching=700");
        media.addOption(":rtsp-tcp");
        media.addOption(":clock-jitter=0");
        media.addOption(":no-audio");
        media.addOption(":drop-late-frames");
        media.addOption(":skip-frames");
        player.setMedia(media); media.release(); player.play();
    }

    private void stopVlc() {
        if (player != null) { player.setEventListener(null); player.stop(); player.detachViews(); player.release(); player = null; }
    }

    private void restartStream() {
        if (currentMediaUrl == null) return;
        String url = currentMediaUrl;
        Log.w("HARM", "Stream stalled; reconnecting");
        stopVlc(); lastProgressAt = SystemClock.elapsedRealtime(); startVlc(url);
    }

    private EditText field(String hint, String value) {
        EditText field = new EditText(this); field.setHint(hint); field.setSingleLine(true); field.setText(value); return field;
    }

    private void showSettings(boolean cancelable) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(28, 20, 28, 8);
        EditText host = field("NVR IP address", prefs.getString(KEY_NVR_HOST, ""));
        EditText user = field("NVR username", prefs.getString(KEY_NVR_USER, "admin"));
        EditText password = field("NVR password", prefs.getString(KEY_NVR_PASSWORD, ""));
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(host); box.addView(user); box.addView(password);
        Button connect = new Button(this); connect.setText("Connect and select cameras"); box.addView(connect);
        TextView selected = new TextView(this); selected.setPadding(0, 8, 0, 4);
        selected.setText(selectionSummary()); box.addView(selected);
        Button preview = new Button(this); preview.setText("Preview camera"); box.addView(preview);
        TextView api = new TextView(this);
        api.setText("Local control port: " + API_PORT + "\nToken: " + prefs.getString(KEY_TOKEN, ""));
        api.setTextIsSelectable(true); api.setPadding(0, 18, 0, 12); box.addView(api);
        Button admin = new Button(this); admin.setText("Allow screen power off");
        admin.setOnClickListener(v -> requestAdmin()); box.addView(admin);
        Button brightness = new Button(this); brightness.setText("Allow brightness control");
        brightness.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName())))); box.addView(brightness);
        connect.setOnClickListener(v -> {
            connect.setEnabled(false); connect.setText("Connecting...");
            String h = host.getText().toString().trim(), u = user.getText().toString().trim(), p = password.getText().toString();
            new Thread(() -> {
                try {
                    List<String> names = NvrClient.channelNames(h, u, p);
                    runOnUiThread(() -> chooseCameras(names, h, u, p, selected, connect));
                } catch (Exception error) {
                    runOnUiThread(() -> { connect.setEnabled(true); connect.setText("Connect and select cameras");
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); });
                }
            }).start();
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("HARM").setView(box)
                .setCancelable(cancelable).setNegativeButton(cancelable ? "Cancel" : null, null)
                .setPositiveButton("Save", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            if (host.getText().toString().trim().isEmpty()) { host.setError("Enter an IP address"); return; }
            prefs.edit().putString(KEY_NVR_HOST, host.getText().toString().trim())
                    .putString(KEY_NVR_USER, user.getText().toString().trim())
                    .putString(KEY_NVR_PASSWORD, password.getText().toString()).apply();
            dialog.dismiss(); showIdle();
        })); dialog.show();
        preview.setOnClickListener(v -> previewCamera(dialog, host, user, password));
    }

    private String selectionSummary() {
        String cameras = prefs.getString(KEY_CAMERAS, "");
        return cameras.isEmpty() ? "No cameras selected" : "Selected cameras: " + cameras;
    }

    private void chooseCameras(List<String> names, String host, String user, String password,
                               TextView selected, Button connect) {
        CharSequence[] labels = new CharSequence[names.size()]; boolean[] checked = new boolean[names.size()];
        String old = "," + prefs.getString(KEY_CAMERAS, "") + ",";
        for (int i = 0; i < names.size(); i++) { labels[i] = (i + 1) + " — " + names.get(i); checked[i] = old.equals(",,") || old.contains("," + (i + 1) + ","); }
        new AlertDialog.Builder(this).setTitle("Select cameras").setMultiChoiceItems(labels, checked, (d, which, value) -> checked[which] = value)
                .setNegativeButton("Cancel", (d, w) -> { connect.setEnabled(true); connect.setText("Connect and select cameras"); })
                .setPositiveButton("Save", (d, w) -> {
                    StringBuilder channels = new StringBuilder(), savedNames = new StringBuilder();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) {
                        if (channels.length() > 0) { channels.append(','); savedNames.append('|'); }
                        channels.append(i + 1); savedNames.append(names.get(i));
                    }
                    prefs.edit().putString(KEY_NVR_HOST, host).putString(KEY_NVR_USER, user).putString(KEY_NVR_PASSWORD, password)
                            .putString(KEY_CAMERAS, channels.toString()).putString(KEY_CAMERA_NAMES, savedNames.toString()).apply();
                    selected.setText(selectionSummary()); connect.setEnabled(true); connect.setText("Connect and select cameras");
                }).show();
    }

    private void previewCamera(AlertDialog settingsDialog, EditText host, EditText user, EditText password) {
        String channelsValue = prefs.getString(KEY_CAMERAS, "");
        if (channelsValue.isEmpty()) { Toast.makeText(this, "Select at least one camera", Toast.LENGTH_LONG).show(); return; }
        String[] channels = channelsValue.split(",");
        String[] savedNames = prefs.getString(KEY_CAMERA_NAMES, "").split("\\|", -1);
        CharSequence[] labels = new CharSequence[channels.length];
        for (int i = 0; i < channels.length; i++) {
            String name = i < savedNames.length && !savedNames[i].isEmpty() ? savedNames[i] : "Channel " + channels[i];
            labels[i] = channels[i] + " — " + name;
        }
        new AlertDialog.Builder(this).setTitle("Preview camera").setItems(labels, (d, which) -> {
            prefs.edit().putString(KEY_NVR_HOST, host.getText().toString().trim())
                    .putString(KEY_NVR_USER, user.getText().toString().trim())
                    .putString(KEY_NVR_PASSWORD, password.getText().toString()).apply();
            settingsDialog.dismiss(); playCamera(Integer.parseInt(channels[which]), 0);
        }).setNegativeButton("Cancel", null).show();
    }

    private void requestAdmin() {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, AdminReceiver.class));
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allows Home Assistant to turn off the screen."); startActivity(i);
    }

    @Override public void onBackPressed() { showSettings(true); }
    @Override protected void onDestroy() {
        if (receiver != null) unregisterReceiver(receiver); showIdle();
        if (watchdog != null) handler.removeCallbacks(watchdog);
        if (libVLC != null) { libVLC.release(); libVLC = null; }
        super.onDestroy();
    }
}
