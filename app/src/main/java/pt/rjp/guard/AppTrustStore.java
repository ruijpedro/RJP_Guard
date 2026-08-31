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
 * Regras locais de confiança e histórico resumido das apps que originam notificações.
 *
 * ANALYZE: comportamento normal do RJP Guard.
 * TRUSTED: a app é considerada legítima; avisos amarelos de conteúdo fraco são
 *          suprimidos, mas padrões fortes de possível burla continuam a alertar.
 * IGNORE: o utilizador pediu explicitamente para não analisar notificações desta app.
 *
 * A V3.5 passa também a guardar, por app, quantas notificações foram realmente
 * escrutinadas, a última análise, o último resultado e quantos alertas foram gerados.
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
        public final long lastScrutinized;
        public final long scrutinyCount;
        public final long alertCount;
        public final String lastVerdict;

        AppRule(String packageName, String label, String mode, long lastSeen,
                long lastScrutinized, long scrutinyCount, long alertCount, String lastVerdict) {
            this.packageName = packageName == null ? "" : packageName;
            this.label = label == null || label.trim().isEmpty() ? this.packageName : label.trim();
            this.mode = normalizeMode(mode);
            this.lastSeen = lastSeen;
            this.lastScrutinized = lastScrutinized;
            this.scrutinyCount = Math.max(0L, scrutinyCount);
            this.alertCount = Math.max(0L, alertCount);
            this.lastVerdict = lastVerdict == null ? "" : lastVerdict.trim();
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Regista que a app foi vista pelo NotificationListener, sem assumir que o conteúdo foi analisado. */
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
            if (!item.has("lastScrutinized")) item.put("lastScrutinized", 0L);
            if (!item.has("scrutinyCount")) item.put("scrutinyCount", 0L);
            if (!item.has("alertCount")) item.put("alertCount", 0L);
            if (!item.has("lastVerdict")) item.put("lastVerdict", "");
            all.put(pkg, item);
            write(c, all);
        } catch (Exception ignored) {}
    }

    /**
     * Regista uma análise efetiva do conteúdo da notificação.
     * Chamado mesmo quando o resultado é SEGURO, para que "Apps observadas" mostre
     * quais apps foram realmente escrutinadas pelo motor local.
     */
    public static void recordScrutiny(Context c, String packageName, String label, String verdict, boolean alertGenerated) {
        if (c == null || packageName == null || packageName.trim().isEmpty()) return;
        String pkg = packageName.trim();
        try {
            JSONObject all = read(c);
            JSONObject item = all.optJSONObject(pkg);
            if (item == null) item = new JSONObject();
            item.put("label", label == null || label.trim().isEmpty() ? pkg : label.trim());
            item.put("mode", normalizeMode(item.optString("mode", MODE_ANALYZE)));
            long now = System.currentTimeMillis();
            item.put("lastSeen", now);
            item.put("lastScrutinized", now);
            item.put("scrutinyCount", item.optLong("scrutinyCount", 0L) + 1L);
            if (alertGenerated) item.put("alertCount", item.optLong("alertCount", 0L) + 1L);
            else if (!item.has("alertCount")) item.put("alertCount", 0L);
            item.put("lastVerdict", verdict == null ? "" : verdict.trim());
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
            if (!item.has("lastScrutinized")) item.put("lastScrutinized", 0L);
            if (!item.has("scrutinyCount")) item.put("scrutinyCount", 0L);
            if (!item.has("alertCount")) item.put("alertCount", 0L);
            if (!item.has("lastVerdict")) item.put("lastVerdict", "");
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
            result.add(toRule(pkg, item));
        }
        Collections.sort(result, Comparator.comparing(a -> a.label.toLowerCase()));
        return result;
    }

    /** Apps realmente analisadas, mais recente primeiro. */
    public static List<AppRule> scrutinized(Context c) {
        List<AppRule> result = new ArrayList<>();
        for (AppRule rule : all(c)) if (rule.scrutinyCount > 0L) result.add(rule);
        Collections.sort(result, (a, b) -> Long.compare(b.lastScrutinized, a.lastScrutinized));
        return result;
    }

    public static int configuredCount(Context c) {
        int count = 0;
        for (AppRule rule : all(c)) if (!MODE_ANALYZE.equals(rule.mode)) count++;
        return count;
    }

    public static int observedCount(Context c) { return all(c).size(); }

    public static int scrutinizedAppCount(Context c) { return scrutinized(c).size(); }

    public static long totalScrutinyCount(Context c) {
        long count = 0L;
        for (AppRule rule : all(c)) count += rule.scrutinyCount;
        return count;
    }

    public static String modeLabel(String mode) {
        String m = normalizeMode(mode);
        if (MODE_TRUSTED.equals(m)) return "🟢 CONFIÁVEL • analisar só risco forte";
        if (MODE_IGNORE.equals(m)) return "⚪ IGNORAR notificações";
        return "🛡️ ANALISAR normalmente";
    }

    private static AppRule toRule(String pkg, JSONObject item) {
        return new AppRule(
                pkg,
                item.optString("label", pkg),
                item.optString("mode", MODE_ANALYZE),
                item.optLong("lastSeen", 0L),
                item.optLong("lastScrutinized", 0L),
                item.optLong("scrutinyCount", 0L),
                item.optLong("alertCount", 0L),
                item.optString("lastVerdict", "")
        );
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
