package pt.rjp.guard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Regras locais de confiança e histórico resumido das apps que originam notificações.
 *
 * ANALYZE: comportamento normal do RJP Guard.
 * SYSTEM_TRUSTED: app/sistema reconhecido como confiável; notificações são registadas
 *                 como escrutinadas/seguras, mas não geram alertas de conteúdo.
 * TRUSTED: app considerada legítima; avisos amarelos de conteúdo fraco são suprimidos,
 *          mas padrões fortes de possível burla continuam a alertar.
 * IGNORE: o utilizador pediu explicitamente para não analisar notificações desta app.
 */
public final class AppTrustStore {
    public static final String MODE_ANALYZE = "ANALYZE";
    public static final String MODE_SYSTEM_TRUSTED = "SYSTEM_TRUSTED";
    public static final String MODE_TRUSTED = "TRUSTED";
    public static final String MODE_IGNORE = "IGNORE";

    private static final String PREFS = "rjp_guard_app_trust";
    private static final String RULES = "rules_json";

    /**
     * Lista conservadora de componentes oficiais que costumam originar notificações
     * de atualização, permissões ou manutenção do Android. O utilizador pode sempre
     * substituir esta regra manualmente dentro do RJP Guard.
     */
    private static final Set<String> DEFAULT_SYSTEM_TRUSTED = new HashSet<>();
    static {
        DEFAULT_SYSTEM_TRUSTED.add("com.android.vending");                 // Google Play Store
        DEFAULT_SYSTEM_TRUSTED.add("com.google.android.gms");             // Google Play services
        DEFAULT_SYSTEM_TRUSTED.add("com.google.android.gsf");             // Google Services Framework
        DEFAULT_SYSTEM_TRUSTED.add("com.android.systemui");               // Android System UI
        DEFAULT_SYSTEM_TRUSTED.add("com.android.permissioncontroller");   // Permission Controller (AOSP)
        DEFAULT_SYSTEM_TRUSTED.add("com.google.android.permissioncontroller");
        DEFAULT_SYSTEM_TRUSTED.add("com.android.packageinstaller");
        DEFAULT_SYSTEM_TRUSTED.add("com.google.android.packageinstaller");
        DEFAULT_SYSTEM_TRUSTED.add("com.google.android.packageinstaller.permission.ui");
    }

    private static final Set<String> MESSAGE_APPS = new HashSet<>();
    static {
        MESSAGE_APPS.add("com.whatsapp");
        MESSAGE_APPS.add("org.telegram.messenger");
        MESSAGE_APPS.add("com.facebook.orca");
        MESSAGE_APPS.add("com.google.android.apps.messaging");
        MESSAGE_APPS.add("com.android.mms");
        MESSAGE_APPS.add("com.samsung.android.messaging");
        MESSAGE_APPS.add("com.microsoft.office.outlook");
        MESSAGE_APPS.add("com.google.android.gm");
    }

    private AppTrustStore() {}

    public static final class AppRule {
        public final String packageName;
        public final String label;
        public final String mode;
        public final long lastSeen;
        public final long lastScrutinized;
        public final long scrutinyCount;
        public final long alertCount;
        public final long safeEventCount;
        public final long updateEventCount;
        public final String lastVerdict;
        public final String lastClassification;
        public final boolean userConfigured;

        AppRule(String packageName, String label, String mode, long lastSeen,
                long lastScrutinized, long scrutinyCount, long alertCount,
                long safeEventCount, long updateEventCount,
                String lastVerdict, String lastClassification, boolean userConfigured) {
            this.packageName = packageName == null ? "" : packageName;
            this.label = label == null || label.trim().isEmpty() ? this.packageName : label.trim();
            this.mode = normalizeMode(mode);
            this.lastSeen = lastSeen;
            this.lastScrutinized = lastScrutinized;
            this.scrutinyCount = Math.max(0L, scrutinyCount);
            this.alertCount = Math.max(0L, alertCount);
            this.safeEventCount = Math.max(0L, safeEventCount);
            this.updateEventCount = Math.max(0L, updateEventCount);
            this.lastVerdict = lastVerdict == null ? "" : lastVerdict.trim();
            this.lastClassification = lastClassification == null ? "" : lastClassification.trim();
            this.userConfigured = userConfigured;
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

            boolean hadMode = item.has("mode");
            String oldMode = normalizeMode(item.optString("mode", MODE_ANALYZE));
            boolean userConfigured = item.optBoolean("userConfigured", false);

            // Migração suave das versões anteriores: regras TRUSTED/IGNORE já escolhidas
            // pelo utilizador continuam intocadas. Apps conhecidas do sistema que ainda
            // estavam no modo ANALYZE passam para o novo modo SYSTEM_TRUSTED.
            if (!item.has("userConfigured") && hadMode && !MODE_ANALYZE.equals(oldMode)) {
                userConfigured = true;
            }

            String mode = oldMode;
            if (!userConfigured && isDefaultSystemTrustedPackage(pkg)) {
                mode = MODE_SYSTEM_TRUSTED;
            }

            item.put("label", label == null || label.trim().isEmpty() ? pkg : label.trim());
            item.put("mode", mode);
            item.put("userConfigured", userConfigured);
            item.put("lastSeen", System.currentTimeMillis());
            if (!item.has("lastScrutinized")) item.put("lastScrutinized", 0L);
            if (!item.has("scrutinyCount")) item.put("scrutinyCount", 0L);
            if (!item.has("alertCount")) item.put("alertCount", 0L);
            if (!item.has("safeEventCount")) item.put("safeEventCount", 0L);
            if (!item.has("updateEventCount")) item.put("updateEventCount", 0L);
            if (!item.has("lastVerdict")) item.put("lastVerdict", "");
            if (!item.has("lastClassification")) item.put("lastClassification", "");
            all.put(pkg, item);
            write(c, all);
        } catch (Exception ignored) {}
    }

    /** Regista uma análise efetiva do conteúdo da notificação. */
    public static void recordScrutiny(Context c, String packageName, String label, String verdict, boolean alertGenerated) {
        if (c == null || packageName == null || packageName.trim().isEmpty()) return;
        String pkg = packageName.trim();
        try {
            JSONObject all = read(c);
            JSONObject item = all.optJSONObject(pkg);
            if (item == null) item = new JSONObject();
            item.put("label", label == null || label.trim().isEmpty() ? pkg : label.trim());
            item.put("mode", normalizeMode(item.optString("mode", defaultModeForPackage(pkg))));
            if (!item.has("userConfigured")) item.put("userConfigured", false);
            long now = System.currentTimeMillis();
            item.put("lastSeen", now);
            item.put("lastScrutinized", now);
            item.put("scrutinyCount", item.optLong("scrutinyCount", 0L) + 1L);
            if (alertGenerated) item.put("alertCount", item.optLong("alertCount", 0L) + 1L);
            else if (!item.has("alertCount")) item.put("alertCount", 0L);
            item.put("lastVerdict", verdict == null ? "" : verdict.trim());
            item.put("lastClassification", "ANÁLISE DE RISCO");
            all.put(pkg, item);
            write(c, all);
        } catch (Exception ignored) {}
    }

    /** Regista uma atualização/solicitação reconhecida como segura. */
    public static void recordSafeEvent(Context c, String packageName, String label, String verdict, String classification, boolean update) {
        if (c == null || packageName == null || packageName.trim().isEmpty()) return;
        String pkg = packageName.trim();
        try {
            JSONObject all = read(c);
            JSONObject item = all.optJSONObject(pkg);
            if (item == null) item = new JSONObject();
            item.put("label", label == null || label.trim().isEmpty() ? pkg : label.trim());
            item.put("mode", normalizeMode(item.optString("mode", defaultModeForPackage(pkg))));
            if (!item.has("userConfigured")) item.put("userConfigured", false);
            long now = System.currentTimeMillis();
            item.put("lastSeen", now);
            item.put("lastScrutinized", now);
            item.put("scrutinyCount", item.optLong("scrutinyCount", 0L) + 1L);
            item.put("safeEventCount", item.optLong("safeEventCount", 0L) + 1L);
            if (update) item.put("updateEventCount", item.optLong("updateEventCount", 0L) + 1L);
            else if (!item.has("updateEventCount")) item.put("updateEventCount", 0L);
            if (!item.has("alertCount")) item.put("alertCount", 0L);
            item.put("lastVerdict", verdict == null ? "🟢 SEGURO" : verdict.trim());
            item.put("lastClassification", classification == null ? "SOLICITAÇÃO SEGURA" : classification.trim());
            all.put(pkg, item);
            write(c, all);
        } catch (Exception ignored) {}
    }

    public static String modeFor(Context c, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return MODE_ANALYZE;
        String pkg = packageName.trim();
        JSONObject item = read(c).optJSONObject(pkg);
        return item == null ? defaultModeForPackage(pkg)
                : normalizeMode(item.optString("mode", defaultModeForPackage(pkg)));
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
            item.put("userConfigured", true);
            if (!item.has("lastSeen")) item.put("lastSeen", 0L);
            if (!item.has("lastScrutinized")) item.put("lastScrutinized", 0L);
            if (!item.has("scrutinyCount")) item.put("scrutinyCount", 0L);
            if (!item.has("alertCount")) item.put("alertCount", 0L);
            if (!item.has("safeEventCount")) item.put("safeEventCount", 0L);
            if (!item.has("updateEventCount")) item.put("updateEventCount", 0L);
            if (!item.has("lastVerdict")) item.put("lastVerdict", "");
            if (!item.has("lastClassification")) item.put("lastClassification", "");
            all.put(pkg, item);
            write(c, all);
        } catch (Exception ignored) {}
    }

    /** Repõe a decisão automática do RJP Guard para esta app. */
    public static void resetToDefault(Context c, String packageName, String label) {
        if (c == null || packageName == null || packageName.trim().isEmpty()) return;
        String pkg = packageName.trim();
        try {
            JSONObject all = read(c);
            JSONObject item = all.optJSONObject(pkg);
            if (item == null) item = new JSONObject();
            item.put("label", label == null || label.trim().isEmpty() ? item.optString("label", pkg) : label.trim());
            item.put("mode", defaultModeForPackage(pkg));
            item.put("userConfigured", false);
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
        for (AppRule rule : all(c)) if (rule.userConfigured) count++;
        return count;
    }

    public static int systemTrustedCount(Context c) {
        int count = 0;
        for (AppRule rule : all(c)) if (MODE_SYSTEM_TRUSTED.equals(rule.mode)) count++;
        return count;
    }

    public static int observedCount(Context c) { return all(c).size(); }
    public static int scrutinizedAppCount(Context c) { return scrutinized(c).size(); }

    public static long totalScrutinyCount(Context c) {
        long count = 0L;
        for (AppRule rule : all(c)) count += rule.scrutinyCount;
        return count;
    }

    public static long totalSafeEventCount(Context c) {
        long count = 0L;
        for (AppRule rule : all(c)) count += rule.safeEventCount;
        return count;
    }

    public static long totalUpdateEventCount(Context c) {
        long count = 0L;
        for (AppRule rule : all(c)) count += rule.updateEventCount;
        return count;
    }

    public static String modeLabel(String mode) {
        String m = normalizeMode(mode);
        if (MODE_SYSTEM_TRUSTED.equals(m)) return "🟢 SISTEMA/CONFIÁVEL • não alertar notificações normais";
        if (MODE_TRUSTED.equals(m)) return "🛡️ CONFIÁVEL • analisar só risco forte";
        if (MODE_IGNORE.equals(m)) return "⚪ IGNORAR notificações";
        return "👁️ OBSERVAR / ANALISAR normalmente";
    }

    public static boolean isDefaultSystemTrustedPackage(String packageName) {
        return packageName != null && DEFAULT_SYSTEM_TRUSTED.contains(packageName.trim());
    }

    public static boolean isMessagingPackage(String packageName) {
        if (packageName == null) return false;
        return MESSAGE_APPS.contains(packageName.trim());
    }

    public static String recommendationFor(String packageName) {
        if (isDefaultSystemTrustedPackage(packageName)) {
            return "SISTEMA/CONFIÁVEL — origem oficial conhecida; não alertar notificações normais.";
        }
        if (isMessagingPackage(packageName)) {
            return "ANALISAR ou CONFIÁVEL — a app é legítima, mas o remetente/conteúdo pode ser fraudulento.";
        }
        return "ANALISAR — manter observação até o utilizador conhecer e classificar esta app.";
    }

    private static String defaultModeForPackage(String pkg) {
        return isDefaultSystemTrustedPackage(pkg) ? MODE_SYSTEM_TRUSTED : MODE_ANALYZE;
    }

    private static AppRule toRule(String pkg, JSONObject item) {
        return new AppRule(
                pkg,
                item.optString("label", pkg),
                item.optString("mode", defaultModeForPackage(pkg)),
                item.optLong("lastSeen", 0L),
                item.optLong("lastScrutinized", 0L),
                item.optLong("scrutinyCount", 0L),
                item.optLong("alertCount", 0L),
                item.optLong("safeEventCount", 0L),
                item.optLong("updateEventCount", 0L),
                item.optString("lastVerdict", ""),
                item.optString("lastClassification", ""),
                item.optBoolean("userConfigured", false)
        );
    }

    private static String normalizeMode(String mode) {
        if (MODE_SYSTEM_TRUSTED.equals(mode)) return MODE_SYSTEM_TRUSTED;
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
