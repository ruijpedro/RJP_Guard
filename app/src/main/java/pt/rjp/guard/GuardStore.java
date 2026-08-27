package pt.rjp.guard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GuardStore {
    private static final String PREFS = "rjp_guard_store";
    private static final String SAFE = "safe_numbers";
    private static final String BLOCKED = "blocked_numbers";
    private static final String HISTORY = "history_json";
    private static final String AUTO_BLOCK_PUBLIC = "auto_block_public";
    private static final String LOCAL_REPORTS = "local_reports_json";

    private GuardStore() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static void addSafe(Context c, String number) {
        String n = normalizeNumber(number);
        addToSet(c, SAFE, n);
        removeFromSet(c, BLOCKED, n);
        removeLocalReport(c, n);
    }

    public static void addBlocked(Context c, String number) { addToSet(c, BLOCKED, normalizeNumber(number)); }

    public static void reportLocal(Context c, String number, String category) {
        String n = normalizeNumber(number);
        if (n.isEmpty()) return;
        addToSet(c, BLOCKED, n);
        JSONObject obj = localReports(c);
        try { obj.put(SpamDatabase.canonical(n), category == null ? "Outro" : category); } catch (JSONException ignored) {}
        prefs(c).edit().putString(LOCAL_REPORTS, obj.toString()).apply();
    }

    public static String localReportType(Context c, String number) {
        String key = SpamDatabase.canonical(number);
        if (key.isEmpty()) return "";
        JSONObject obj = localReports(c);
        String exact = obj.optString(key, "");
        if (!exact.isEmpty()) return exact;
        if (key.length() == 9) return obj.optString("351" + key, "");
        if (key.length() == 12 && key.startsWith("351")) return obj.optString(key.substring(3), "");
        return "";
    }

    private static JSONObject localReports(Context c) {
        try { return new JSONObject(prefs(c).getString(LOCAL_REPORTS, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    private static void removeLocalReport(Context c, String number) {
        JSONObject obj = localReports(c);
        String key = SpamDatabase.canonical(number);
        obj.remove(key);
        if (key.length() == 9) obj.remove("351" + key);
        if (key.length() == 12 && key.startsWith("351")) obj.remove(key.substring(3));
        prefs(c).edit().putString(LOCAL_REPORTS, obj.toString()).apply();
    }

    public static boolean isSafe(Context c, String number) { return matches(prefs(c).getStringSet(SAFE, Collections.emptySet()), normalizeNumber(number)); }
    public static boolean isBlocked(Context c, String number) { return matches(prefs(c).getStringSet(BLOCKED, Collections.emptySet()), normalizeNumber(number)); }
    public static int safeCount(Context c) { return prefs(c).getStringSet(SAFE, Collections.emptySet()).size(); }
    public static int blockedCount(Context c) { return prefs(c).getStringSet(BLOCKED, Collections.emptySet()).size(); }

    private static void addToSet(Context c, String key, String number) {
        if (number.isEmpty()) return;
        Set<String> set = new HashSet<>(prefs(c).getStringSet(key, Collections.emptySet())); set.add(number);
        prefs(c).edit().putStringSet(key, set).apply();
    }

    private static void removeFromSet(Context c, String key, String number) {
        Set<String> set = new HashSet<>(prefs(c).getStringSet(key, Collections.emptySet()));
        set.removeIf(stored -> sameNumber(stored, number));
        prefs(c).edit().putStringSet(key, set).apply();
    }

    private static boolean matches(Set<String> set, String candidate) {
        if (candidate.isEmpty()) return false;
        for (String stored : set) if (sameNumber(stored, candidate)) return true;
        return false;
    }

    private static boolean sameNumber(String a, String b) {
        String aa = SpamDatabase.canonical(a), bb = SpamDatabase.canonical(b);
        if (aa.equals(bb)) return true;
        return aa.length() >= 9 && bb.length() >= 9 && aa.substring(aa.length()-9).equals(bb.substring(bb.length()-9));
    }

    public static String normalizeNumber(String value) { return value == null ? "" : value.replaceAll("[^0-9+]", ""); }
    public static boolean autoBlockPublic(Context c) { return prefs(c).getBoolean(AUTO_BLOCK_PUBLIC, false); }
    public static void setAutoBlockPublic(Context c, boolean enabled) { prefs(c).edit().putBoolean(AUTO_BLOCK_PUBLIC, enabled).apply(); }

    public static void addHistory(Context c, String entry) {
        List<String> list = getHistory(c); list.add(0, entry); while (list.size() > 40) list.remove(list.size()-1);
        JSONArray array = new JSONArray(); for (String item : list) array.put(item);
        prefs(c).edit().putString(HISTORY, array.toString()).apply();
    }

    public static List<String> getHistory(Context c) {
        String raw = prefs(c).getString(HISTORY, "[]"); List<String> list = new ArrayList<>();
        try { JSONArray array = new JSONArray(raw); for (int i=0;i<array.length();i++) list.add(array.optString(i)); } catch (JSONException ignored) {}
        return list;
    }
}
