package pt.rjp.guard;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SpamSyncManager {
    public static final String SOURCE_NAME = "CallShield Community Database";
    public static final String SOURCE_URL = "https://raw.githubusercontent.com/SysAdminDoc/CallShield/master/data/spam_numbers.json";
    private static final long MAX_BYTES = 24L * 1024L * 1024L;
    private static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onFinished(boolean ok, String message, long count);
    }

    private SpamSyncManager() {}

    public static void autoSyncIfNeeded(Context context) {
        autoSyncIfNeeded(context, null);
    }

    public static void autoSyncIfNeeded(Context context, Callback callback) {
        long last = 0;
        try { last = Long.parseLong(SpamDatabase.get(context).meta("last_sync_ms", "0")); } catch (Exception ignored) {}
        if (System.currentTimeMillis() - last > WEEK_MS) sync(context, callback);
    }

    public static void sync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean ok = false;
            String message;
            long count = SpamDatabase.get(app).count();
            File temp = new File(app.getCacheDir(), "rjp_guard_spam_download.json");
            try {
                download(temp);
                int imported = importJson(app, temp);
                count = SpamDatabase.get(app).count();
                ok = imported > 0;
                message = ok ? "Base anti-spam atualizada" : "A atualização não trouxe números válidos";
            } catch (Exception e) {
                message = "Não foi possível atualizar a base. A proteção local existente continua ativa.";
            } finally {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
            if (callback != null) {
                boolean finalOk = ok;
                String finalMessage = message;
                long finalCount = count;
                new Handler(Looper.getMainLooper()).post(() -> callback.onFinished(finalOk, finalMessage, finalCount));
            }
        });
    }

    private static void download(File target) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(SOURCE_URL).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "RJP-Guard/3.1");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        long declared = conn.getContentLengthLong();
        if (declared > MAX_BYTES) throw new IllegalStateException("Feed demasiado grande");
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                total += n;
                if (total > MAX_BYTES) throw new IllegalStateException("Feed demasiado grande");
                out.write(buffer, 0, n);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static int importJson(Context context, File file) throws Exception {
        SpamDatabase helper = SpamDatabase.get(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        int[] imported = {0};
        String[] version = {""};
        String[] updated = {""};

        db.beginTransaction();
        try (JsonReader reader = new JsonReader(new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8), 32 * 1024))) {
            helper.replaceBegin(db);
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("version".equals(name)) {
                    version[0] = readAsString(reader);
                } else if ("updated".equals(name) || "updated_at".equals(name) || "generated_at".equals(name)) {
                    updated[0] = readAsString(reader);
                } else if ("numbers".equals(name) || "spam_numbers".equals(name) || "data".equals(name)) {
                    readNumbersArray(reader, helper, db, imported);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            if (imported[0] <= 0) throw new IllegalStateException("Feed vazio ou incompatível");
            helper.putMeta(db, "source", SOURCE_NAME);
            helper.putMeta(db, "source_url", SOURCE_URL);
            helper.putMeta(db, "version", version[0]);
            helper.putMeta(db, "source_updated", updated[0]);
            helper.putMeta(db, "last_sync_ms", String.valueOf(System.currentTimeMillis()));
            helper.putMeta(db, "last_sync", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return imported[0];
    }

    private static void readNumbersArray(JsonReader reader, SpamDatabase helper, SQLiteDatabase db, int[] imported) throws Exception {
        reader.beginArray();
        while (reader.hasNext()) {
            switch (reader.peek()) {
                case STRING:
                    helper.insert(db, reader.nextString(), "spam", 0, "");
                    imported[0]++;
                    break;
                case BEGIN_OBJECT:
                    String number = "";
                    String type = "spam";
                    String description = "";
                    int reports = 0;
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String key = reader.nextName();
                        if ("number".equals(key) || "phone".equals(key) || "phone_number".equals(key)) {
                            number = readAsString(reader);
                        } else if ("type".equals(key) || "category".equals(key)) {
                            type = readAsString(reader);
                        } else if ("reports".equals(key) || "report_count".equals(key)) {
                            reports = readAsInt(reader);
                        } else if ("description".equals(key) || "reason".equals(key)) {
                            description = readAsString(reader);
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                    if (!number.isEmpty()) {
                        helper.insert(db, number, type, reports, description);
                        imported[0]++;
                    }
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endArray();
    }

    private static String readAsString(JsonReader reader) throws Exception {
        switch (reader.peek()) {
            case STRING: return reader.nextString();
            case NUMBER: return reader.nextString();
            case BOOLEAN: return String.valueOf(reader.nextBoolean());
            case NULL: reader.nextNull(); return "";
            default: reader.skipValue(); return "";
        }
    }

    private static int readAsInt(JsonReader reader) throws Exception {
        try {
            if (reader.peek() == android.util.JsonToken.NUMBER) return reader.nextInt();
            String s = readAsString(reader);
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
