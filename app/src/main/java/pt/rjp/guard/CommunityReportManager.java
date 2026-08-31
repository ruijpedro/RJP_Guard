package pt.rjp.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CommunityReportManager {
    private static final String PREFS = "rjp_guard_community";
    private static final String INSTALL_ID = "anonymous_installation_id";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback { void onFinished(boolean ok, String message); }

    private CommunityReportManager() {}

    public static void report(Context context, String number, String category, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean ok = false;
            String message;
            try {
                JSONObject payload = new JSONObject();
                payload.put("acao", "report");
                payload.put("numero", normalizeForApi(number));
                payload.put("categoria", category == null ? "Outro" : category);
                payload.put("instalacao", installationId(app));
                payload.put("pais", countryFor(number));
                payload.put("appVersao", appVersion(app));
                payload.put("observacoes", "Reporte voluntário RJP Guard");

                JSONObject response = postJson(CommunitySyncManager.ENDPOINT, payload);
                ok = response.optBoolean("ok", false);
                if (ok && response.optBoolean("duplicado", false)) {
                    message = "Este número já tinha sido reportado por este telemóvel.";
                } else if (ok) {
                    message = "Reporte partilhado com a RJP Guard Community.";
                } else {
                    message = "Reporte guardado localmente; não foi possível partilhá-lo agora.";
                }
            } catch (Exception e) {
                message = "Reporte guardado localmente; não foi possível partilhá-lo agora.";
            }
            if (callback != null) {
                boolean fOk = ok; String fMessage = message;
                new Handler(Looper.getMainLooper()).post(() -> callback.onFinished(fOk, fMessage));
            }
        });
    }

    public static String installationId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String current = prefs.getString(INSTALL_ID, "");
        if (current != null && current.length() >= 12) return current;
        String id = UUID.randomUUID().toString().replace("-", "");
        prefs.edit().putString(INSTALL_ID, id).apply();
        return id;
    }

    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            String version = info.versionName;
            return (version == null || version.trim().isEmpty()) ? "3.6.1" : version;
        } catch (PackageManager.NameNotFoundException e) {
            return "3.6.1";
        }
    }

    private static JSONObject postJson(String address, JSONObject payload) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "RJP-Guard/3.6.1");
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) { out.write(body); }
        int code = conn.getResponseCode();
        try (BufferedInputStream in = new BufferedInputStream(code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n; int total = 0;
            while ((n = in.read(buffer)) >= 0) {
                total += n; if (total > 1024 * 1024) throw new IllegalStateException("Resposta demasiado grande");
                out.write(buffer, 0, n);
            }
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
        } finally {
            conn.disconnect();
        }
    }

    private static String normalizeForApi(String value) {
        String raw = value == null ? "" : value.trim().replaceAll("[()\\s\\-.]", "");
        if (raw.startsWith("00")) raw = "+" + raw.substring(2);
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 9) return "+351" + digits;
        if (digits.startsWith("351") && digits.length() == 12) return "+" + digits;
        return raw.startsWith("+") ? "+" + digits : (digits.length() >= 10 ? "+" + digits : digits);
    }

    private static String countryFor(String number) {
        String n = normalizeForApi(number).toUpperCase(Locale.ROOT);
        if (n.startsWith("+351")) return "PT";
        if (n.startsWith("+34")) return "ES";
        if (n.startsWith("+33")) return "FR";
        if (n.startsWith("+352")) return "LU";
        if (n.startsWith("+49")) return "DE";
        if (n.startsWith("+44")) return "UK";
        if (n.startsWith("+1")) return "US/CA";
        return "INT";
    }
}
