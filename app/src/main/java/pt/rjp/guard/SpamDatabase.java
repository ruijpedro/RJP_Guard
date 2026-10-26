package pt.rjp.guard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpamDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "rjp_guard_spam.db";
    private static final int DB_VERSION = 4;
    private static SpamDatabase instance;
    private static final Pattern DOMAIN = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?([a-z0-9][a-z0-9.-]+\\.[a-z]{2,24})");

    public static final class Hit {
        public final String number;
        public final String source;
        public final String type;
        public final int reports;
        public final String description;
        public final String sourceDate;

        Hit(String number, String source, String type, int reports, String description, String sourceDate) {
            this.number = number; this.source = source; this.type = type; this.reports = reports;
            this.description = description; this.sourceDate = sourceDate;
        }
    }

    public static final class RangeHit {
        public final String prefix;
        public final String source;
        public final String type;
        public final int reports;
        public final String description;
        RangeHit(String prefix, String source, String type, int reports, String description) {
            this.prefix=prefix; this.source=source; this.type=type; this.reports=reports; this.description=description;
        }
    }

    public static synchronized SpamDatabase get(Context context) {
        if (instance == null) instance = new SpamDatabase(context.getApplicationContext());
        return instance;
    }

    private SpamDatabase(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE spam_numbers (number TEXT NOT NULL, source TEXT NOT NULL, type TEXT, reports INTEGER DEFAULT 0, description TEXT, source_date TEXT, PRIMARY KEY(number, source))");
        db.execSQL("CREATE TABLE spam_ranges (prefix TEXT NOT NULL, source TEXT NOT NULL, type TEXT, reports INTEGER DEFAULT 0, description TEXT, source_date TEXT, PRIMARY KEY(prefix, source))");
        db.execSQL("CREATE TABLE spam_domains (domain TEXT NOT NULL, source TEXT NOT NULL, reports INTEGER DEFAULT 0, description TEXT, source_date TEXT, PRIMARY KEY(domain, source))");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)");
        db.execSQL("CREATE INDEX idx_spam_number ON spam_numbers(number)");
        db.execSQL("CREATE INDEX idx_spam_source ON spam_numbers(source)");
        db.execSQL("CREATE INDEX idx_range_prefix ON spam_ranges(prefix)");
        db.execSQL("CREATE INDEX idx_domain_name ON spam_domains(domain)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE spam_numbers RENAME TO spam_numbers_old");
            db.execSQL("CREATE TABLE spam_numbers (number TEXT NOT NULL, source TEXT NOT NULL, type TEXT, reports INTEGER DEFAULT 0, description TEXT, source_date TEXT, PRIMARY KEY(number, source))");
            db.execSQL("INSERT OR REPLACE INTO spam_numbers(number, source, type, reports, description, source_date) SELECT number, COALESCE(NULLIF(source,''),'CallShield'), type, reports, description, '' FROM spam_numbers_old");
            db.execSQL("DROP TABLE spam_numbers_old");
            db.execSQL("CREATE INDEX idx_spam_number ON spam_numbers(number)");
            db.execSQL("CREATE INDEX idx_spam_source ON spam_numbers(source)");
        }
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS spam_ranges (prefix TEXT NOT NULL, source TEXT NOT NULL, type TEXT, reports INTEGER DEFAULT 0, description TEXT, source_date TEXT, PRIMARY KEY(prefix, source))");
            db.execSQL("CREATE TABLE IF NOT EXISTS spam_domains (domain TEXT NOT NULL, source TEXT NOT NULL, reports INTEGER DEFAULT 0, description TEXT, source_date TEXT, PRIMARY KEY(domain, source))");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_range_prefix ON spam_ranges(prefix)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_domain_name ON spam_domains(domain)");
        }
    }

    public boolean isKnownSpam(String rawNumber) { return !hits(rawNumber).isEmpty(); }

    public List<Hit> hits(String rawNumber) {
        List<Hit> out = new ArrayList<>();
        List<String> candidates = candidates(rawNumber);
        if (candidates.isEmpty()) return out;
        StringBuilder q = new StringBuilder("SELECT number,source,type,reports,description,source_date FROM spam_numbers WHERE number IN (");
        for (int i=0;i<candidates.size();i++) { if (i>0) q.append(','); q.append('?'); }
        q.append(") ORDER BY CASE source WHEN 'CallShield Hot' THEN 0 WHEN 'CallShield' THEN 1 WHEN 'RJP Guard Community' THEN 2 WHEN 'FTC DNC' THEN 3 ELSE 9 END, reports DESC");
        try (Cursor c = getReadableDatabase().rawQuery(q.toString(), candidates.toArray(new String[0]))) {
            while (c.moveToNext()) out.add(new Hit(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getString(4), c.getString(5)));
        }
        return out;
    }

    public List<RangeHit> rangeHits(String rawNumber) {
        List<RangeHit> out = new ArrayList<>();
        String n = canonical(rawNumber);
        if (n.isEmpty()) return out;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT prefix,source,type,reports,description FROM spam_ranges WHERE ? LIKE prefix || '%' ORDER BY LENGTH(prefix) DESC, reports DESC",
                new String[]{n})) {
            while (c.moveToNext()) out.add(new RangeHit(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getString(4)));
        }
        return out;
    }

    public String findSpamDomain(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher m = DOMAIN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String host = m.group(1);
            if (host == null) continue;
            host = host.replaceAll("^www\\.", "");
            String candidate = host;
            while (candidate.contains(".")) {
                try (Cursor c = getReadableDatabase().rawQuery("SELECT domain FROM spam_domains WHERE domain=? LIMIT 1", new String[]{candidate})) {
                    if (c.moveToFirst()) return c.getString(0);
                }
                int dot = candidate.indexOf('.');
                candidate = dot >= 0 ? candidate.substring(dot + 1) : "";
            }
        }
        return "";
    }

    public String getType(String rawNumber) {
        List<Hit> h = hits(rawNumber); return h.isEmpty() || h.get(0).type == null ? "spam" : h.get(0).type;
    }
    public String getDescription(String rawNumber) {
        List<Hit> h = hits(rawNumber); return h.isEmpty() || h.get(0).description == null ? "" : h.get(0).description;
    }
    public String getSource(String rawNumber) {
        List<Hit> h = hits(rawNumber); if (h.isEmpty()) return "";
        StringBuilder s = new StringBuilder();
        for (Hit hit : h) { if (s.indexOf(hit.source) < 0) { if (s.length()>0) s.append(" + "); s.append(hit.source); } }
        return s.toString();
    }

    public long count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(DISTINCT number) FROM spam_numbers", null)) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
    public long countBySource(String source) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM spam_numbers WHERE source=?", new String[]{source})) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
    public long countRangesBySource(String source) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM spam_ranges WHERE source=?", new String[]{source})) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
    public long countDomainsBySource(String source) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM spam_domains WHERE source=?", new String[]{source})) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }

    public String meta(String key, String fallback) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?", new String[]{key})) { return c.moveToFirst() ? c.getString(0) : fallback; }
    }
    public void putMeta(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues(); cv.put("key", key); cv.put("value", value == null ? "" : value);
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void replaceSourceBegin(SQLiteDatabase db, String source) { db.delete("spam_numbers", "source=?", new String[]{source}); }
    public void replaceRangesSourceBegin(SQLiteDatabase db, String source) { db.delete("spam_ranges", "source=?", new String[]{source}); }
    public void replaceDomainsSourceBegin(SQLiteDatabase db, String source) { db.delete("spam_domains", "source=?", new String[]{source}); }

    public void insert(SQLiteDatabase db, String rawNumber, String source, String type, int reports, String description, String sourceDate) {
        String number = canonical(rawNumber);
        if (number.length() < 6 || number.length() > 18 || source == null || source.trim().isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put("number", number); cv.put("source", source.trim()); cv.put("type", type == null ? "spam" : type);
        cv.put("reports", Math.max(0, reports)); cv.put("description", description == null ? "" : description);
        cv.put("source_date", sourceDate == null ? "" : sourceDate);
        db.insertWithOnConflict("spam_numbers", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void insertRange(SQLiteDatabase db, String rawPrefix, String source, String type, int reports, String description, String sourceDate) {
        String prefix = canonical(rawPrefix);
        if (prefix.length() < 4 || prefix.length() > 15 || source == null || source.trim().isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put("prefix",prefix); cv.put("source",source.trim()); cv.put("type",type==null?"campaign":type);
        cv.put("reports",Math.max(0,reports)); cv.put("description",description==null?"":description); cv.put("source_date",sourceDate==null?"":sourceDate);
        db.insertWithOnConflict("spam_ranges",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void insertDomain(SQLiteDatabase db, String rawDomain, String source, int reports, String description, String sourceDate) {
        if (rawDomain == null || source == null) return;
        String domain = rawDomain.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://","").replaceFirst("^www\\.","");
        int slash=domain.indexOf('/'); if(slash>=0) domain=domain.substring(0,slash);
        if (!domain.contains(".") || domain.length()>253) return;
        ContentValues cv=new ContentValues(); cv.put("domain",domain); cv.put("source",source.trim()); cv.put("reports",Math.max(0,reports));
        cv.put("description",description==null?"":description); cv.put("source_date",sourceDate==null?"":sourceDate);
        db.insertWithOnConflict("spam_domains",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static List<String> candidates(String rawNumber) {
        ArrayList<String> out = new ArrayList<>();
        String n = canonical(rawNumber);
        if (n.isEmpty()) return out;
        out.add(n);
        if (n.length() == 9) out.add("351" + n);
        if (n.length() == 12 && n.startsWith("351")) out.add(n.substring(3));
        if (n.length() == 10) out.add("1" + n);
        if (n.length() == 11 && n.startsWith("1")) out.add(n.substring(1));
        return out;
    }

    public static String canonical(String value) { return value == null ? "" : value.replaceAll("[^0-9]", ""); }
}
