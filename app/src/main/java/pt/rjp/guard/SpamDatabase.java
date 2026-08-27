package pt.rjp.guard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class SpamDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "rjp_guard_spam.db";
    private static final int DB_VERSION = 2;
    private static SpamDatabase instance;

    public static synchronized SpamDatabase get(Context context) {
        if (instance == null) instance = new SpamDatabase(context.getApplicationContext());
        return instance;
    }

    private SpamDatabase(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE spam_numbers (number TEXT PRIMARY KEY, type TEXT, reports INTEGER DEFAULT 0, description TEXT, source TEXT DEFAULT 'CallShield')");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)");
        db.execSQL("CREATE INDEX idx_spam_number ON spam_numbers(number)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS spam_numbers");
        db.execSQL("DROP TABLE IF EXISTS meta");
        onCreate(db);
    }

    public boolean isKnownSpam(String rawNumber) { return resolveNumber(rawNumber) != null; }

    private String resolveNumber(String rawNumber) {
        String n = canonical(rawNumber);
        if (n.isEmpty()) return null;
        SQLiteDatabase db = getReadableDatabase();
        if (exists(db, n)) return n;
        if (n.length() == 9) {
            String pt = "351" + n;
            if (exists(db, pt)) return pt;
        }
        if (n.length() == 12 && n.startsWith("351")) {
            String local = n.substring(3);
            if (exists(db, local)) return local;
        }
        return null;
    }

    private boolean exists(SQLiteDatabase db, String n) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM spam_numbers WHERE number=? LIMIT 1", new String[]{n})) { return c.moveToFirst(); }
    }

    public String getType(String rawNumber) { return field(rawNumber, "type", "spam"); }
    public String getDescription(String rawNumber) { return field(rawNumber, "description", ""); }
    public String getSource(String rawNumber) { return field(rawNumber, "source", "CallShield"); }

    private String field(String rawNumber, String field, String fallback) {
        String resolved = resolveNumber(rawNumber);
        if (resolved == null) return fallback;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + field + " FROM spam_numbers WHERE number=? LIMIT 1", new String[]{resolved})) {
            return c.moveToFirst() && c.getString(0) != null ? c.getString(0) : fallback;
        }
    }

    public long count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM spam_numbers", null)) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }

    public String meta(String key, String fallback) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?", new String[]{key})) { return c.moveToFirst() ? c.getString(0) : fallback; }
    }

    public void putMeta(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues(); cv.put("key", key); cv.put("value", value == null ? "" : value);
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void replaceBegin(SQLiteDatabase db) { db.delete("spam_numbers", null, null); }

    public void insert(SQLiteDatabase db, String rawNumber, String type, int reports, String description) {
        String number = canonical(rawNumber);
        if (number.length() < 6 || number.length() > 18) return;
        ContentValues cv = new ContentValues();
        cv.put("number", number); cv.put("type", type == null ? "spam" : type); cv.put("reports", Math.max(0, reports));
        cv.put("description", description == null ? "" : description); cv.put("source", "CallShield");
        db.insertWithOnConflict("spam_numbers", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static String canonical(String value) { return value == null ? "" : value.replaceAll("[^0-9]", ""); }
}
