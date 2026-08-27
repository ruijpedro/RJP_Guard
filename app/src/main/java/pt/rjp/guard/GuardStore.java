package pt.rjp.guard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

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

    private GuardStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void addSafe(Context c, String number) {
        addToSet(c, SAFE, normalizeNumber(number));
    }

    public static void addBlocked(Context c, String number) {
        addToSet(c, BLOCKED, normalizeNumber(number));
    }

    public static boolean isSafe(Context c, String number) {
        return matches(prefs(c).getStringSet(SAFE, Collections.emptySet()), normalizeNumber(number));
    }

    public static boolean isBlocked(Context c, String number) {
        return matches(prefs(c).getStringSet(BLOCKED, Collections.emptySet()), normalizeNumber(number));
    }

    public static int safeCount(Context c) {
        return prefs(c).getStringSet(SAFE, Collections.emptySet()).size();
    }

    public static int blockedCount(Context c) {
        return prefs(c).getStringSet(BLOCKED, Collections.emptySet()).size();
    }

    private static void addToSet(Context c, String key, String number) {
        if (number.isEmpty()) return;
        Set<String> set = new HashSet<>(prefs(c).getStringSet(key, Collections.emptySet()));
        set.add(number);
        prefs(c).edit().putStringSet(key, set).apply();
    }

    private static boolean matches(Set<String> set, String candidate) {
        if (candidate.isEmpty()) return false;
        for (String stored : set) {
            if (stored.equals(candidate)) return true;
            if (stored.length() >= 9 && candidate.length() >= 9 &&
                    stored.substring(stored.length() - 9).equals(candidate.substring(candidate.length() - 9))) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeNumber(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9+]", "");
    }


    public static boolean autoBlockPublic(Context c) {
        return prefs(c).getBoolean(AUTO_BLOCK_PUBLIC, false);
    }

    public static void setAutoBlockPublic(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(AUTO_BLOCK_PUBLIC, enabled).apply();
    }

    public static void addHistory(Context c, String entry) {
        List<String> list = getHistory(c);
        list.add(0, entry);
        while (list.size() > 30) list.remove(list.size() - 1);
        JSONArray array = new JSONArray();
        for (String item : list) array.put(item);
        prefs(c).edit().putString(HISTORY, array.toString()).apply();
    }

    public static List<String> getHistory(Context c) {
        String raw = prefs(c).getString(HISTORY, "[]");
        List<String> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) list.add(array.optString(i));
        } catch (JSONException ignored) {}
        return list;
    }
}
