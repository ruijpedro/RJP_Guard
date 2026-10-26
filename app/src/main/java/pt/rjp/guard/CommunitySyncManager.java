package pt.rjp.guard;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CommunitySyncManager {
    public static final String SOURCE_NAME = "RJP Guard Community";
    public static final String ENDPOINT = "https://script.google.com/macros/s/AKfycbzKB1z-XRZyA3gl2Os1VCvAFpob1y92aM9SvsLjq2BfhA51aiVxgtkw9tKVLFCjDWmm4Q/exec";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback { void onFinished(boolean ok, String message, long count); }

    private CommunitySyncManager() {}

    public static void autoSyncIfNeeded(Context context, Callback callback) {
        long last = parseLong(SpamDatabase.get(context).meta("community_last_sync_ms", "0"));
        if (System.currentTimeMillis() - last > DAY_MS) sync(context, callback);
    }

    public static void sync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean ok = false;
            String message;
            long count = SpamDatabase.get(app).countBySource(SOURCE_NAME);
            try {
                String raw = getJson(ENDPOINT + "?acao=export");
                int imported = importJson(app, raw);
                count = SpamDatabase.get(app).countBySource(SOURCE_NAME);
                ok = imported >= 0;
                message = "RJP Guard Community atualizada";
            } catch (Exception e) {
                message = "Não foi possível atualizar a RJP Guard Community. A base local existente continua ativa.";
            }
            if (callback != null) {
                boolean fOk = ok; String fMessage = message; long fCount = count;
                new Handler(Looper.getMainLooper()).post(() -> callback.onFinished(fOk, fMessage, fCount));
            }
        });
    }

    private static String getJson(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(25000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "RJP-Guard/3.7");
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        try (InputStream in = new BufferedInputStream(conn.getInputStream()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int n; int total = 0;
            while ((n = in.read(buffer)) >= 0) {
                total += n;
                if (total > MAX_BYTES) throw new IllegalStateException("Resposta demasiado grande");
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            conn.disconnect();
        }
    }

    private static int importJson(Context context, String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        if (!root.optBoolean("ok", false)) throw new IllegalStateException(root.optString("erro", "Resposta inválida"));
        JSONArray numbers = root.optJSONArray("numeros");
        if (numbers == null) numbers = new JSONArray();

        SpamDatabase helper = SpamDatabase.get(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        int imported = 0;
        db.beginTransaction();
        try {
            helper.replaceSourceBegin(db, SOURCE_NAME);
            for (int i = 0; i < numbers.length(); i++) {
                JSONObject item = numbers.optJSONObject(i);
                if (item == null) continue;
                String number = item.optString("numero", "");
                String category = item.optString("categoria", "Reportado");
                String state = item.optString("estado", "Pendente");
                int reports = item.optInt("reportes", 0);
                int users = item.optInt("utilizadores", 0);
                String updated = item.optString("atualizado", "");
                if (number.isEmpty() || "Removido".equalsIgnoreCase(state)) continue;
                String description = "Estado comunitário: " + state + " • " + users + " utilizador(es) • " + reports + " reporte(s)";
                helper.insert(db, number, SOURCE_NAME, category, reports, description, updated);
                imported++;
            }
            String now = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
            helper.putMeta(db, "community_last_sync_ms", String.valueOf(System.currentTimeMillis()));
            helper.putMeta(db, "community_last_sync", now);
            helper.putMeta(db, "community_endpoint", ENDPOINT);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return imported;
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return 0L; }
    }
}
