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

public final class CallShieldTrendSyncManager {
    public static final String HOT_NUMBER_SOURCE = "CallShield Hot";
    public static final String HOT_RANGE_SOURCE = "CallShield Hot Range";
    public static final String DOMAIN_SOURCE = "CallShield Domains";
    public static final String HOT_NUMBERS_URL = "https://raw.githubusercontent.com/SysAdminDoc/CallShield/master/data/hot_numbers.json";
    public static final String HOT_RANGES_URL = "https://raw.githubusercontent.com/SysAdminDoc/CallShield/master/data/hot_ranges.json";
    public static final String SPAM_DOMAINS_URL = "https://raw.githubusercontent.com/SysAdminDoc/CallShield/master/data/spam_domains.json";

    private static final long HALF_HOUR_MS = 30L * 60L * 1000L;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback { void onFinished(boolean ok, String message); }
    private CallShieldTrendSyncManager() {}

    public static void autoSyncIfNeeded(Context context) {
        long last = parseLong(SpamDatabase.get(context).meta("callshield_trends_last_sync_ms", "0"));
        if (System.currentTimeMillis() - last > HALF_HOUR_MS) sync(context, null);
    }

    public static void sync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean ok = false;
            String message;
            try {
                JSONObject hotNumbers = new JSONObject(downloadText(HOT_NUMBERS_URL));
                JSONObject hotRanges = new JSONObject(downloadText(HOT_RANGES_URL));
                JSONObject domains = new JSONObject(downloadText(SPAM_DOMAINS_URL));
                importAll(app, hotNumbers, hotRanges, domains);
                ok = true;
                long n = SpamDatabase.get(app).countBySource(HOT_NUMBER_SOURCE);
                long r = SpamDatabase.get(app).countRangesBySource(HOT_RANGE_SOURCE);
                long d = SpamDatabase.get(app).countDomainsBySource(DOMAIN_SOURCE);
                message = "CallShield tendências: " + n + " números • " + r + " gamas • " + d + " domínios";
            } catch (Exception e) {
                message = "Não foi possível atualizar tendências CallShield; os dados locais existentes continuam ativos.";
            }
            if (callback != null) {
                boolean fOk = ok; String fMessage = message;
                new Handler(Looper.getMainLooper()).post(() -> callback.onFinished(fOk, fMessage));
            }
        });
    }

    private static void importAll(Context context, JSONObject hotNumbers, JSONObject hotRanges, JSONObject domains) throws Exception {
        SpamDatabase helper = SpamDatabase.get(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            helper.replaceSourceBegin(db, HOT_NUMBER_SOURCE);
            helper.replaceRangesSourceBegin(db, HOT_RANGE_SOURCE);
            helper.replaceDomainsSourceBegin(db, DOMAIN_SOURCE);

            String hotGenerated = hotNumbers.optString("generated", "");
            JSONArray nums = hotNumbers.optJSONArray("numbers");
            if (nums != null) {
                for (int i=0;i<nums.length();i++) {
                    Object item = nums.opt(i);
                    if (item instanceof String) {
                        helper.insert(db, (String)item, HOT_NUMBER_SOURCE, "trending_spam", 0, "Em campanha recente CallShield", hotGenerated);
                    } else if (item instanceof JSONObject) {
                        JSONObject o=(JSONObject)item;
                        String number=first(o,"number","phone","phone_number");
                        int reports=o.optInt("reports",o.optInt("report_count",o.optInt("count",0)));
                        String category=first(o,"type","category","label");
                        if(category.isEmpty()) category="trending_spam";
                        helper.insert(db,number,HOT_NUMBER_SOURCE,category,reports,"Em campanha recente CallShield",hotGenerated);
                    }
                }
            }

            String rangeGenerated = hotRanges.optString("generated", "");
            JSONArray ranges = hotRanges.optJSONArray("ranges");
            if (ranges != null) {
                for (int i=0;i<ranges.length();i++) {
                    Object item=ranges.opt(i);
                    if(item instanceof String) {
                        helper.insertRange(db,(String)item,HOT_RANGE_SOURCE,"campaign",0,"Gama associada a campanha recente",rangeGenerated);
                    } else if(item instanceof JSONObject) {
                        JSONObject o=(JSONObject)item;
                        String prefix=first(o,"prefix","range","npa_nxx","number_prefix");
                        int reports=o.optInt("reports",o.optInt("report_count",o.optInt("count",0)));
                        helper.insertRange(db,prefix,HOT_RANGE_SOURCE,"campaign",reports,"Gama associada a campanha recente",rangeGenerated);
                    }
                }
            }

            String domainGenerated=domains.optString("generated", "");
            JSONArray arr=domains.optJSONArray("domains");
            if(arr!=null) {
                for(int i=0;i<arr.length();i++) {
                    Object item=arr.opt(i);
                    if(item instanceof String) {
                        helper.insertDomain(db,(String)item,DOMAIN_SOURCE,0,"Domínio reportado em mensagens CallShield",domainGenerated);
                    } else if(item instanceof JSONObject) {
                        JSONObject o=(JSONObject)item;
                        String domain=first(o,"domain","host","hostname");
                        int reports=o.optInt("reports",o.optInt("report_count",o.optInt("count",0)));
                        helper.insertDomain(db,domain,DOMAIN_SOURCE,reports,"Domínio reportado em mensagens CallShield",domainGenerated);
                    }
                }
            }

            String now=new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
            helper.putMeta(db,"callshield_trends_last_sync_ms",String.valueOf(System.currentTimeMillis()));
            helper.putMeta(db,"callshield_trends_last_sync",now);
            helper.putMeta(db,"callshield_hot_generated",hotGenerated);
            helper.putMeta(db,"callshield_ranges_generated",rangeGenerated);
            helper.putMeta(db,"callshield_domains_generated",domainGenerated);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static String downloadText(String url) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(10000); conn.setReadTimeout(15000); conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent","RJP-Guard/4.0");
        int code=conn.getResponseCode();
        if(code<200 || code>=300) throw new IllegalStateException("HTTP "+code);
        try(InputStream in=new BufferedInputStream(conn.getInputStream()); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[8192]; int n,total=0;
            while((n=in.read(buf))!=-1) { total+=n; if(total>MAX_BYTES) throw new IllegalStateException("Feed demasiado grande"); out.write(buf,0,n); }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally { conn.disconnect(); }
    }

    private static String first(JSONObject o,String... keys) {
        for(String k:keys) { String v=o.optString(k,"").trim(); if(!v.isEmpty()) return v; }
        return "";
    }
    private static long parseLong(String s) { try{return Long.parseLong(s);}catch(Exception e){return 0L;} }
}
