package pt.rjp.guard;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.JsonToken;

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
    public static final String SOURCE_NAME = "CallShield";
    public static final String SOURCE_LABEL = "CallShield Community Database";
    public static final String SOURCE_URL = "https://raw.githubusercontent.com/SysAdminDoc/CallShield/master/data/spam_numbers.json";
    private static final long MAX_BYTES = 32L * 1024L * 1024L;
    private static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback { void onFinished(boolean ok, String message, long count); }
    private SpamSyncManager() {}

    public static void autoSyncIfNeeded(Context context) { autoSyncIfNeeded(context, null); }
    public static void autoSyncIfNeeded(Context context, Callback callback) {
        long last = parseLong(SpamDatabase.get(context).meta("callshield_last_sync_ms", SpamDatabase.get(context).meta("last_sync_ms", "0")));
        if (System.currentTimeMillis() - last > WEEK_MS) sync(context, callback);
    }

    public static void sync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean ok = false; String message; long count = SpamDatabase.get(app).countBySource(SOURCE_NAME);
            File temp = new File(app.getCacheDir(), "rjp_guard_callshield.json");
            try {
                download(temp); int imported = importJson(app, temp); count = SpamDatabase.get(app).countBySource(SOURCE_NAME);
                ok = imported > 0; message = ok ? "CallShield atualizada" : "A atualização CallShield não trouxe números válidos";
            } catch (Exception e) { message = "Não foi possível atualizar a CallShield. A base local existente continua ativa."; }
            finally { //noinspection ResultOfMethodCallIgnored
                temp.delete(); }
            if (callback != null) { boolean fOk=ok; String fMessage=message; long fCount=count; new Handler(Looper.getMainLooper()).post(() -> callback.onFinished(fOk,fMessage,fCount)); }
        });
    }

    private static void download(File target) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(SOURCE_URL).openConnection();
        conn.setConnectTimeout(12000); conn.setReadTimeout(30000); conn.setRequestProperty("User-Agent","RJP-Guard/3.6"); conn.setInstanceFollowRedirects(true);
        int code=conn.getResponseCode(); if(code<200||code>=300) throw new IllegalStateException("HTTP "+code);
        long declared=conn.getContentLengthLong(); if(declared>MAX_BYTES) throw new IllegalStateException("Feed demasiado grande");
        try(InputStream in=new BufferedInputStream(conn.getInputStream()); FileOutputStream out=new FileOutputStream(target)){
            byte[] buffer=new byte[16*1024]; long total=0; int n;
            while((n=in.read(buffer))>=0){ total+=n; if(total>MAX_BYTES) throw new IllegalStateException("Feed demasiado grande"); out.write(buffer,0,n); }
        } finally { conn.disconnect(); }
    }

    private static int importJson(Context context, File file) throws Exception {
        SpamDatabase helper=SpamDatabase.get(context); SQLiteDatabase db=helper.getWritableDatabase(); int[] imported={0}; String[] version={""}; String[] updated={""};
        db.beginTransaction();
        try(JsonReader reader=new JsonReader(new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),32*1024))){
            helper.replaceSourceBegin(db,SOURCE_NAME);
            reader.beginObject();
            while(reader.hasNext()){
                String name=reader.nextName();
                if("version".equals(name)) version[0]=readAsString(reader);
                else if("updated".equals(name)||"updated_at".equals(name)||"generated_at".equals(name)) updated[0]=readAsString(reader);
                else if("numbers".equals(name)||"spam_numbers".equals(name)||"data".equals(name)) readNumbersArray(reader,helper,db,imported,updated[0]);
                else reader.skipValue();
            }
            reader.endObject();
            if(imported[0]<=0) throw new IllegalStateException("Feed vazio ou incompatível");
            String now=new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
            helper.putMeta(db,"callshield_version",version[0]); helper.putMeta(db,"callshield_source_updated",updated[0]);
            helper.putMeta(db,"callshield_last_sync_ms",String.valueOf(System.currentTimeMillis())); helper.putMeta(db,"callshield_last_sync",now);
            // legacy meta for compatibility with V3.2 UI state during upgrade
            helper.putMeta(db,"version",version[0]); helper.putMeta(db,"last_sync_ms",String.valueOf(System.currentTimeMillis())); helper.putMeta(db,"last_sync",now);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return imported[0];
    }

    private static void readNumbersArray(JsonReader reader, SpamDatabase helper, SQLiteDatabase db, int[] imported, String feedDate) throws Exception {
        reader.beginArray();
        while(reader.hasNext()){
            if(reader.peek()==JsonToken.STRING){ helper.insert(db,reader.nextString(),SOURCE_NAME,"spam",0,"",feedDate); imported[0]++; continue; }
            if(reader.peek()!=JsonToken.BEGIN_OBJECT){ reader.skipValue(); continue; }
            String number="",type="spam",description="",rowSource=""; int reports=0;
            reader.beginObject();
            while(reader.hasNext()){
                String key=reader.nextName();
                if("number".equals(key)||"phone".equals(key)||"phone_number".equals(key)) number=readAsString(reader);
                else if("type".equals(key)||"category".equals(key)) type=readAsString(reader);
                else if("reports".equals(key)||"report_count".equals(key)||"count".equals(key)) reports=readAsInt(reader);
                else if("description".equals(key)||"reason".equals(key)||"label".equals(key)) description=readAsString(reader);
                else if("source".equals(key)||"provenance".equals(key)) rowSource=readAsString(reader);
                else reader.skipValue();
            }
            reader.endObject();
            if(!number.isEmpty()){
                if(!rowSource.isEmpty() && !description.toLowerCase(Locale.ROOT).contains("fonte")) description=(description.isEmpty()?"":""+description+" • ")+"Origem agregada: "+rowSource;
                helper.insert(db,number,SOURCE_NAME,type,reports,description,feedDate); imported[0]++;
            }
        }
        reader.endArray();
    }

    private static String readAsString(JsonReader reader) throws Exception {
        switch(reader.peek()){
            case STRING:return reader.nextString(); case NUMBER:return reader.nextString(); case BOOLEAN:return String.valueOf(reader.nextBoolean()); case NULL:reader.nextNull();return ""; default:reader.skipValue();return "";
        }
    }
    private static int readAsInt(JsonReader reader) throws Exception { try { if(reader.peek()==JsonToken.NUMBER)return reader.nextInt(); return Integer.parseInt(readAsString(reader)); } catch(Exception e){return 0;} }
    private static long parseLong(String s){ try{return Long.parseLong(s);}catch(Exception e){return 0;} }
}
