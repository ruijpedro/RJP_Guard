package pt.rjp.guard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Regras locais de confiança para apps que originam notificações.
 *
 * ANALYZE: comportamento normal do RJP Guard.
 * TRUSTED: a app é considerada legítima; avisos amarelos de conteúdo fraco são
 *          suprimidos, mas padrões fortes de possível burla continuam a alertar.
 * IGNORE: o utilizador pediu explicitamente para não analisar notificações desta app.
 */
public final class AppTrustStore {
    public static final String MODE_ANALYZE = "ANALYZE";
    public static final String MODE_TRUSTED = "TRUSTED";
    public static final String MODE_IGNORE = "IGNORE";

    private static final String PREFS = "rjp_guard_app_trust";
    private static final String RULES = "rules_json";

    private AppTrustStore() {}

    public static final class AppRule {
        public final String packageName;
        public final String label;
        public final String mode;
        public final long lastSeen;

        AppRule(String packageName, String label, String mode, long lastSeen) {
            this.packageName = packageName == null ? "" : packageName;
            this.label = label == null || label.trim().isEmpty() ? this.packageName : label.trim();
            this.mode = normalizeMode(mode);
            this.lastSeen = lastSeen;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void observeApp(Context c, String packageName, String label) {
        if (c == null || packageName == null || packageName.trim().isEmpty()) return;
        String pkg = packageName.trim();
        try {
            JSONObject all = read(c);
            JSONObject item = all.optJSONObject(pkg);
            if (item == null) item = new JSONObject();
            item.put("label", label == null || label.trim().isEmpty() ? pkg : label.trim());
            item.put("mode", normalizeMode(item.optString("mode", MODE_ANALYZE)));
            item.put("lastSeen", System.currentTimeMillis());
            all.put(pkg, item);
            write(c, all);
        } catch (Exception ignored) {}
    }

    public static String modeFor(Context c, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return MODE_ANALYZE;
        JSONObject item = read(c).optJSONObject(packageName.trim());
        return item == null ? MODE_ANALYZE : normalizeMode(item.optString("mode", MODE_ANALYZE));
    }

    public static void setMode(Context c, String packageName, String label, String mode) {
        if (c == null || packageName == null || packageName.trim().isEmpty()) return;
        String pkg = packageName.trim();
        try {
            JSONObject all = read(c);
            JSONObject item = all.optJSONObject(pkg);
            if (item == null) item = new JSONObject();
            item.put("label", label == null || label.trim().isEmpty() ? item.optString("label", pkg) : label.trim());
            item.put("mode", normalizeMode(mode));
            if (!item.has("lastSeen")) item.put("lastSeen", 0L);
            all.put(pkg, item);
            write(c, all);
        } catch (Exception ignored) {}
    }

    public static List<AppRule> all(Context c) {
        JSONObject all = read(c);
        List<AppRule> result = new ArrayList<>();
        Iterator<String> keys = all.keys();
        while (keys.hasNext()) {
            String pkg = keys.next();
            JSONObject item = all.optJSONObject(pkg);
            if (item == null) continue;
            result.add(new AppRule(
                    pkg,
                    item.optString("label", pkg),
                    item.optString("mode", MODE_ANALYZE),
                    item.optLong("lastSeen", 0L)
            ));
        }
        Collections.sort(result, Comparator.comparing(a -> a.label.toLowerCase()));
        return result;
    }

    public static int configuredCount(Context c) {
        int count = 0;
        for (AppRule rule : all(c)) if (!MODE_ANALYZE.equals(rule.mode)) count++;
        return count;
    }

    public static int observedCount(Context c) { return all(c).size(); }

    public static String modeLabel(String mode) {
        String m = normalizeMode(mode);
        if (MODE_TRUSTED.equals(m)) return "🟢 CONFIÁVEL • analisar só risco forte";
        if (MODE_IGNORE.equals(m)) return "⚪ IGNORAR notificações";
        return "🛡️ ANALISAR normalmente";
    }

    private static String normalizeMode(String mode) {
        if (MODE_TRUSTED.equals(mode)) return MODE_TRUSTED;
        if (MODE_IGNORE.equals(mode)) return MODE_IGNORE;
        return MODE_ANALYZE;
    }

    private static JSONObject read(Context c) {
        try { return new JSONObject(prefs(c).getString(RULES, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    private static void write(Context c, JSONObject all) {
        prefs(c).edit().putString(RULES, all.toString()).apply();
    }
}
