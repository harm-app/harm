package br.com.harm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NvrClient {
    private NvrClient() { }

    static List<String> channelNames(String host, String user, String password) throws Exception {
        String path = "/cgi-bin/configManager.cgi?action=getConfig&name=ChannelTitle";
        URL url = new URL("http://" + host + path);
        HttpURLConnection first = (HttpURLConnection) url.openConnection();
        first.setConnectTimeout(5000); first.setReadTimeout(5000);
        int firstCode = first.getResponseCode();
        String challenge = first.getHeaderField("WWW-Authenticate");
        first.disconnect();

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        if (firstCode == 401 && challenge != null && challenge.toLowerCase().startsWith("digest"))
            connection.setRequestProperty("Authorization", digest(challenge, user, password, path));
        int code = connection.getResponseCode();
        if (code != 200) throw new Exception("Authentication rejected (HTTP " + code + ")");
        List<String> names = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        Pattern linePattern = Pattern.compile("table\\.ChannelTitle\\[(\\d+)]\\.Name=(.*)");
        String line;
        while ((line = reader.readLine()) != null) {
            Matcher match = linePattern.matcher(line.trim());
            if (match.matches()) names.add(match.group(2).isEmpty() ? "Channel " + (names.size() + 1) : match.group(2));
        }
        reader.close(); connection.disconnect();
        if (names.isEmpty()) throw new Exception("The NVR did not report any channels");
        return names;
    }

    private static String digest(String challenge, String user, String password, String uri) throws Exception {
        Map<String,String> p = new HashMap<>();
        Matcher matcher = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^, ]+))").matcher(challenge);
        while (matcher.find()) p.put(matcher.group(1).toLowerCase(), matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        String realm = p.get("realm"), nonce = p.get("nonce"), qop = p.get("qop"), opaque = p.get("opaque");
        if (realm == null || nonce == null) throw new Exception("Desafio Digest inválido");
        String nc = "00000001", cnonce = Long.toHexString(System.nanoTime());
        String ha1 = md5(user + ":" + realm + ":" + password);
        String ha2 = md5("GET:" + uri);
        String response = qop != null ? md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2)
                : md5(ha1 + ":" + nonce + ":" + ha2);
        String value = "Digest username=\"" + user + "\", realm=\"" + realm + "\", nonce=\"" + nonce
                + "\", uri=\"" + uri + "\", response=\"" + response + "\"";
        if (qop != null) value += ", qop=auth, nc=" + nc + ", cnonce=\"" + cnonce + "\"";
        if (opaque != null) value += ", opaque=\"" + opaque + "\"";
        return value;
    }

    private static String md5(String value) throws Exception {
        byte[] data = MessageDigest.getInstance("MD5").digest(value.getBytes("ISO-8859-1"));
        StringBuilder result = new StringBuilder();
        for (byte b : data) result.append(String.format("%02x", b & 0xff));
        return result.toString();
    }
}
