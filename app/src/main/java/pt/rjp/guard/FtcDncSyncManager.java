package pt.rjp.guard;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;

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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Imports the FTC Do Not Call daily public CSV files.
 * Important: these are consumer reports of unwanted calls/robocalls and are NOT verified fraud findings.
 */
public final class FtcDncSyncManager {
    public static final String SOURCE_NAME = "FTC DNC";
    public static final String SOURCE_PAGE = "https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data";
    private static final String CSV_TEMPLATE = "https://www.ftc.gov/sites/default/files/DNC_Complaint_Numbers_%s.csv";
    private static final long MAX_BYTES = 8L * 1024L * 1024L;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback { void onFinished(boolean ok, String message, long count); }
    private FtcDncSyncManager() {}

    public static void autoSyncIfNeeded(Context context, Callback callback) {
        long last = parseLong(SpamDatabase.get(context).meta("ftc_last_sync_ms", "0"));
        if (System.currentTimeMillis() - last > DAY_MS) sync(context, callback);
    }

    public static void sync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean ok=false; String message; long count=SpamDatabase.get(app).countBySource(SOURCE_NAME);
            File temp=new File(app.getCacheDir(),"rjp_guard_ftc_dnc.csv");
            try {
                String sourceDate=downloadLatest(temp);
                int imported=importCsv(app,temp,sourceDate);
                count=SpamDatabase.get(app).countBySource(SOURCE_NAME);
                ok=imported>0;
                message=ok ? "FTC DNC atualizada ("+sourceDate+")" : "O ficheiro FTC não trouxe números válidos";
            } catch(Exception e) {
                message="Não foi possível atualizar a FTC DNC. A base local existente continua ativa.";
            } finally { //noinspection ResultOfMethodCallIgnored
                temp.delete(); }
            if(callback!=null){ boolean fOk=ok;String fMessage=message;long fCount=count;new Handler(Looper.getMainLooper()).post(()->callback.onFinished(fOk,fMessage,fCount)); }
        });
    }

    private static String downloadLatest(File target) throws Exception {
        Calendar cal=Calendar.getInstance();
        // FTC files are weekday publications. Search back up to 10 calendar days to survive weekends/holidays.
        for(int i=0;i<10;i++){
            Date d=cal.getTime();
            String iso=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(d);
            String url=String.format(Locale.US,CSV_TEMPLATE,iso);
            if(tryDownload(url,target)) return iso;
            cal.add(Calendar.DAY_OF_YEAR,-1);
        }
        throw new IllegalStateException("Sem ficheiro FTC recente");
    }

    private static boolean tryDownload(String url, File target) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(10000); conn.setReadTimeout(25000); conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent","RJP-Guard/3.7");
        int code=conn.getResponseCode();
        if(code==404){ conn.disconnect(); return false; }
        if(code<200||code>=300){ conn.disconnect(); return false; }
        long declared=conn.getContentLengthLong(); if(declared>MAX_BYTES){conn.disconnect();throw new IllegalStateException("FTC feed demasiado grande");}
        try(InputStream in=new BufferedInputStream(conn.getInputStream());FileOutputStream out=new FileOutputStream(target)){
            byte[] buffer=new byte[16*1024];long total=0;int n;
            while((n=in.read(buffer))>=0){total+=n;if(total>MAX_BYTES)throw new IllegalStateException("FTC feed demasiado grande");out.write(buffer,0,n);}
        } finally { conn.disconnect(); }
        return target.length()>100;
    }

    private static int importCsv(Context context, File file, String sourceDate) throws Exception {
        SpamDatabase helper=SpamDatabase.get(context);SQLiteDatabase db=helper.getWritableDatabase();int imported=0;
        db.beginTransaction();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),32*1024)){
            String header=br.readLine(); if(header==null)throw new IllegalStateException("CSV vazio");
            List<String> headers=parseCsvLine(header); int phoneIndex=findPhoneColumn(headers); int subjectIndex=findColumn(headers,"subject"); int roboIndex=findColumn(headers,"robocall","recorded");
            if(phoneIndex<0) throw new IllegalStateException("Coluna de telefone não encontrada");
            helper.replaceSourceBegin(db,SOURCE_NAME);
            String line;
            while((line=br.readLine())!=null){
                List<String> row=parseCsvLine(line); if(phoneIndex>=row.size())continue;
                String raw=row.get(phoneIndex);String digits=SpamDatabase.canonical(raw);
                if(digits.length()!=10 && digits.length()!=11)continue;
                if(digits.length()==10)digits="1"+digits; // E.164-ish NANP key without + sign
                String subject=subjectIndex>=0&&subjectIndex<row.size()?row.get(subjectIndex).trim():"";
                String robo=roboIndex>=0&&roboIndex<row.size()?row.get(roboIndex).trim():"";
                String type=("Y".equalsIgnoreCase(robo)||"TRUE".equalsIgnoreCase(robo))?"robocall":"unwanted_call";
                String desc="Relato de consumidor à FTC"+(subject.isEmpty()?"":" • "+subject)+". Registo não verificado pela FTC.";
                helper.insert(db,digits,SOURCE_NAME,type,1,desc,sourceDate); imported++;
            }
            if(imported<=0)throw new IllegalStateException("Nenhum número FTC importado");
            String now=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date());
            helper.putMeta(db,"ftc_last_sync_ms",String.valueOf(System.currentTimeMillis()));helper.putMeta(db,"ftc_last_sync",now);helper.putMeta(db,"ftc_source_date",sourceDate);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return imported;
    }

    private static int findPhoneColumn(List<String> headers){
        for(int i=0;i<headers.size();i++){
            String h=norm(headers.get(i));
            if(h.contains("companyphonenumber")||h.contains("phone number")||h.contains("phonenumber")||h.equals("number")||h.contains("caller"))return i;
        }
        return -1;
    }
    private static int findColumn(List<String> headers,String... terms){for(int i=0;i<headers.size();i++){String h=norm(headers.get(i));for(String t:terms)if(h.contains(norm(t)))return i;}return -1;}
    private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replace("_","").replace("-","").trim();}

    private static List<String> parseCsvLine(String line){
        ArrayList<String> out=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<line.length();i++){
            char ch=line.charAt(i);
            if(ch=='\"'){
                if(quoted&&i+1<line.length()&&line.charAt(i+1)=='\"'){cell.append('\"');i++;}
                else quoted=!quoted;
            } else if(ch==','&&!quoted){out.add(cell.toString());cell.setLength(0);} else cell.append(ch);
        }
        out.add(cell.toString());return out;
    }
    private static long parseLong(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
}
