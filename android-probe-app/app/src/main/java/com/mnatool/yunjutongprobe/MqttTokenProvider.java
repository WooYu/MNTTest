package com.mnatool.yunjutongprobe;

import android.util.Base64;

import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;

import javax.crypto.Cipher;

final class MqttTokenProvider {
    private static final String TAG = "ProbeApp";
    private static final int MAX_ENCRYPT_SIZE = 86;
    private static final String SALT = "3c28b87f8a4b342843847bfeb9e3f3f6";
    private static final String DEFAULT_PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCOn9RRfPGnOd7psFdHS5w0+JlV+URq899c3h8w35o6r2KkBN0FdhAn3Q3dMOaCG1vWLMc2iGgNe/xnul2d9W7GrdTgG4KWsGgaIo8+ESlf+QFEutXPsG7u6SziOYu07DaGk7Lriqof2ZEY2GAspSbjXVXr7xTI0Ej16RQmW0ox/QIDAQAB";
    private static final String SPECIAL_PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDBXz2gGb8on23in4ZldrTo6AROnU/olS1C6Os5yEXb8U2gMMD1USxp4tRMEq2uWxMhRbBpy5OqCkFtUoR6jrfxigBWaR6z48qIwcJPWs07hHWzyA23b/HXNvOMXTpA5YSfb9H5UY0iIRXWGXfA7V7VAnaLSGv9IPSHqoiKkYmT8QIDAQAB";

    private MqttTokenProvider() {
    }

    static String usernameForEnv(String env) {
        return baseDomain(env) + "/api";
    }

    static String getToken(String env, String sn, String pwd, String mac) throws Exception {
        String domain = baseDomain(env);
        Log.i(TAG, "getToken: env=" + env + " domain=" + domain + " sn=" + sn);
        String validKey = getValidKey(domain);
        Log.d(TAG, "getToken: validKey obtained");
        String signSource = "type=1&sn=" + sn
                + "&pwd=" + pwd
                + "&mac1=&mac2=" + mac
                + "&mac3=&salt=" + SALT;
        String sign = md5(signSource);
        String encryptSource = "type=1&valid=" + validKey
                + "&sn=" + sn
                + "&pwd=" + pwd
                + "&mac1=&mac2=" + mac
                + "&mac3=&sign=" + sign;
        String encrypted = encrypt(encryptSource, publicKeyForSn(sn));

        JSONObject request = new JSONObject();
        request.put("encrypt", URLEncoder.encode(encrypted, "UTF-8"));
        request.put("password", pwd);
        request.put("serialNo", sn);

        HttpURLConnection connection = (HttpURLConnection) new URL(domain + "/api/base-uc-app/portal/v2/device-login").openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("content-type", "application/json");
        connection.setRequestProperty("accept-language", "zh-CN");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }
        JSONObject response = new JSONObject(readAll(connection));
        int code = response.optInt("code");
        Log.i(TAG, "getToken: response code=" + code);
        if (code != 200) {
            Log.e(TAG, "getToken failed: " + response.optString("message"));
            throw new IllegalStateException(response.optString("message", "device-login failed"));
        }
        Log.i(TAG, "getToken: success");
        return response.getJSONObject("data").getString("token");
    }

    private static String getValidKey(String domain) throws Exception {
        Log.d(TAG, "getValidKey: " + domain);
        HttpURLConnection connection = (HttpURLConnection) new URL(domain + "/api/device/product/auth/valid-key").openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("GET");
        JSONObject response = new JSONObject(readAll(connection));
        Log.d(TAG, "getValidKey: ok");
        return response.getString("data");
    }

    private static String baseDomain(String env) {
        String normalized = env == null ? "testcn" : env.trim().toLowerCase(Locale.US);
        if (normalized.equals("test") || normalized.equals("testcn")) {
            return "https://autel-cloud-gateway-test.auteltech.cn";
        }
        if (normalized.equals("testus")) {
            return "https://autel-cloud-gateway-testus.autel.com";
        }
        if (normalized.equals("pre")) {
            return "https://autel-cloud-gateway-pre.autel.com";
        }
        if (normalized.equals("prodcn")) {
            return "https://autel-cloud-gateway-prodcn.auteltech.cn";
        }
        if (normalized.equals("produs")) {
            return "https://gateway.autel.com";
        }
        if (normalized.equals("prodeu")) {
            return "https://gateway-prodeu.autel.com";
        }
        return normalized;
    }

    private static String publicKeyForSn(String sn) {
        if (sn.startsWith("CXK") || sn.startsWith("CFJV") || sn.startsWith("CBJ") || sn.startsWith("CGJM")) {
            return SPECIAL_PUBLIC_KEY;
        }
        return DEFAULT_PUBLIC_KEY;
    }

    private static String encrypt(String data, String key) throws Exception {
        byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] source = data.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int offset = 0;
        while (offset < source.length) {
            int size = Math.min(MAX_ENCRYPT_SIZE, source.length - offset);
            output.write(cipher.doFinal(source, offset, size));
            offset += size;
        }
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private static String md5(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte item : bytes) {
            builder.append(String.format(Locale.US, "%02x", item & 0xff));
        }
        return builder.toString();
    }

    private static String readAll(HttpURLConnection connection) throws Exception {
        InputStream stream = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }
}
