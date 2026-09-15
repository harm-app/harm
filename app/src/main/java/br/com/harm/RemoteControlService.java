package br.com.harm;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.net.*;
import java.util.*;

public class RemoteControlService extends Service {
    private volatile boolean running;
    private ServerSocket server;

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (!running) { running = true; new Thread(this::serve, "remote-ha-http").start(); }
        return START_STICKY;
    }
    private void serve() {
        try { server = new ServerSocket(MainActivity.API_PORT); while (running) { Socket s = server.accept(); new Thread(() -> handle(s)).start(); } }
        catch (IOException ignored) { running = false; }
    }
    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(3000);
            String request = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8")).readLine();
            if (request == null) return;
            String[] requestParts = request.split(" ");
            if (requestParts.length < 2 || !"GET".equals(requestParts[0])) { reply(socket, 405, "{\"error\":\"method\"}"); return; }
            Map<String,String> q = query(requestParts[1]);
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
            if (!prefs.getString(MainActivity.KEY_TOKEN, "").equals(q.get("token"))) { reply(socket, 401, "{\"error\":\"unauthorized\"}"); return; }
            String path = requestParts[1].split("\\?", 2)[0];
            if ("/status".equals(path)) reply(socket, 200, "{\"status\":\"ok\",\"version\":\"0.4.4\"}");
            else if ("/camera".equals(path)) {
                int channel = Math.max(1, Math.min(32, number(q.get("channel"), 1)));
                int timeout = Math.max(0, number(q.get("timeout"), 0));
                wake(); command("camera", channel, timeout); reply(socket, 200, "{\"status\":\"playing\",\"channel\":" + channel + "}");
            } else if ("/media".equals(path)) {
                String url = q.get("url"); int timeout = Math.max(0, number(q.get("timeout"), 0));
                if (url == null || url.isEmpty()) reply(socket, 400, "{\"error\":\"missing_url\"}");
                else { wake(); media(url, timeout); reply(socket, 200, "{\"status\":\"playing_media\"}"); }
            } else if ("/stop".equals(path) || "/home".equals(path)) { command("stop", 0, 0); reply(socket, 200, "{\"status\":\"stopped\"}"); }
            else if ("/wake".equals(path)) { wake(); reply(socket, 200, "{\"status\":\"awake\"}"); }
            else if ("/screen/off".equals(path)) {
                DevicePolicyManager dpm = (DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
                ComponentName admin = new ComponentName(this, AdminReceiver.class);
                if (dpm.isAdminActive(admin)) { dpm.lockNow(); reply(socket, 200, "{\"status\":\"off\"}"); }
                else reply(socket, 409, "{\"error\":\"device_admin_required\"}");
            } else if ("/brightness".equals(path)) {
                int value = Math.max(0, Math.min(255, number(q.get("value"), 128)));
                if (Settings.System.canWrite(this)) {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
                    reply(socket, 200, "{\"status\":\"brightness\",\"value\":" + value + "}");
                } else reply(socket, 409, "{\"error\":\"write_settings_required\"}");
            } else reply(socket, 404, "{\"error\":\"not_found\"}");
        } catch (Exception ignored) { } finally { try { socket.close(); } catch (IOException ignored) { } }
    }
    private void wake() {
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                "remoteha:wake").acquire(10000);
        Intent i = new Intent(this, MainActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP); startActivity(i);
    }
    private void command(String name, int camera, int timeout) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        if ("camera".equals(name)) prefs.edit().putInt(MainActivity.KEY_PENDING_CAMERA, camera).putInt(MainActivity.KEY_PENDING_TIMEOUT, timeout).apply();
        Intent i = new Intent(MainActivity.ACTION_COMMAND); i.setPackage(getPackageName()); i.putExtra("command", name);
        i.putExtra("camera", camera); i.putExtra("timeout", timeout); sendBroadcast(i);
    }
    private void media(String url, int timeout) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        prefs.edit().putString(MainActivity.KEY_PENDING_MEDIA, url).putInt(MainActivity.KEY_PENDING_TIMEOUT, timeout).apply();
        Intent i = new Intent(MainActivity.ACTION_COMMAND); i.setPackage(getPackageName()); i.putExtra("command", "media");
        i.putExtra("url", url); i.putExtra("timeout", timeout); sendBroadcast(i);
    }
    private Map<String,String> query(String target) throws Exception {
        Map<String,String> map = new HashMap<>(); int at = target.indexOf('?'); if (at < 0) return map;
        for (String pair : target.substring(at + 1).split("&")) { String[] v = pair.split("=", 2);
            map.put(URLDecoder.decode(v[0], "UTF-8"), v.length > 1 ? URLDecoder.decode(v[1], "UTF-8") : ""); }
        return map;
    }
    private int number(String v, int fallback) { try { return Integer.parseInt(v); } catch (Exception ignored) { return fallback; } }
    private void reply(Socket s, int status, String body) throws IOException {
        byte[] data = body.getBytes("UTF-8"); String h = "HTTP/1.1 " + status + " Result\r\nContent-Type: application/json\r\nContent-Length: "
                + data.length + "\r\nConnection: close\r\n\r\n"; OutputStream out = s.getOutputStream(); out.write(h.getBytes("UTF-8")); out.write(data); out.flush();
    }
    @Override public void onDestroy() { running = false; try { if (server != null) server.close(); } catch (IOException ignored) { } super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
