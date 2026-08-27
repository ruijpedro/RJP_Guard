package pt.rjp.guard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class SpamDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "rjp_guard_spam.db";
    private static final int DB_VERSION = 1;
    private static SpamDatabase instance;

    public static synchronized SpamDatabase get(Context context) {
        if (instance == null) instance = new SpamDatabase(context.getApplicationContext());
        return instance;
    }

    private SpamDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE spam_numbers (number TEXT PRIMARY KEY, type TEXT, reports INTEGER DEFAULT 0, description TEXT)");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)");
        db.execSQL("CREATE INDEX idx_spam_number ON spam_numbers(number)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS spam_numbers");
        db.execSQL("DROP TABLE IF EXISTS meta");
        onCreate(db);
    }

    public boolean isKnownSpam(String rawNumber) {
        String n = canonical(rawNumber);
        if (n.isEmpty()) return false;
        SQLiteDatabase db = getReadableDatabase();
        if (exists(db, n)) return true;

        // Portugal: accept the same number with or without the +351 country code.
        if (n.length() == 9 && (n.startsWith("2") || n.startsWith("9"))) {
            return exists(db, "351" + n);
        }
        if (n.length() == 12 && n.startsWith("351")) {
            return exists(db, n.substring(3));
        }
        return false;
    }

    private boolean exists(SQLiteDatabase db, String n) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM spam_numbers WHERE number=? LIMIT 1", new String[]{n})) {
            return c.moveToFirst();
        }
    }

    public String getType(String rawNumber) {
        String n = canonical(rawNumber);
        if (n.isEmpty()) return "spam";
        SQLiteDatabase db = getReadableDatabase();
        String type = typeFor(db, n);
        if (type != null) return type;
        if (n.length() == 9 && (n.startsWith("2") || n.startsWith("9"))) {
            type = typeFor(db, "351" + n);
            if (type != null) return type;
        }
        if (n.length() == 12 && n.startsWith("351")) {
            type = typeFor(db, n.substring(3));
            if (type != null) return type;
        }
        return "spam";
    }

    private String typeFor(SQLiteDatabase db, String number) {
        try (Cursor c = db.rawQuery("SELECT type FROM spam_numbers WHERE number=? LIMIT 1", new String[]{number})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    public long count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM spam_numbers", null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public String meta(String key, String fallback) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : fallback;
        }
    }

    public void putMeta(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value == null ? "" : value);
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void replaceBegin(SQLiteDatabase db) {
        db.delete("spam_numbers", null, null);
    }

    public void insert(SQLiteDatabase db, String rawNumber, String type, int reports, String description) {
        String number = canonical(rawNumber);
        if (number.length() < 6 || number.length() > 18) return;
        ContentValues cv = new ContentValues();
        cv.put("number", number);
        cv.put("type", type == null ? "spam" : type);
        cv.put("reports", Math.max(0, reports));
        cv.put("description", description == null ? "" : description);
        db.insertWithOnConflict("spam_numbers", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static String canonical(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9]", "");
    }
}
