package pt.rjp.guard;

import android.app.Notification;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Reconhece notificações operacionais legítimas de apps que o utilizador/sistema
 * já classificou como confiáveis. A classificação é conservadora:
 * - Google Play / Package Installer: reconhece atualização, instalação e Play Protect;
 * - app marcada CONFIÁVEL: só suprime eventos operacionais claros e não mensagens;
 * - apps de mensagens continuam a passar pelo RiskAnalyzer.
 *
 * O nome visível nunca é suficiente: a decisão parte sempre do packageName real.
 */
public final class TrustedNotificationClassifier {

    public static final class Result {
        public final boolean safe;
        public final boolean update;
        public final String verdict;
        public final String reason;

        Result(boolean safe, boolean update, String verdict, String reason) {
            this.safe = safe;
            this.update = update;
            this.verdict = verdict == null ? "" : verdict;
            this.reason = reason == null ? "" : reason;
        }

        static Result none() {
            return new Result(false, false, "", "");
        }
    }

    private TrustedNotificationClassifier() {}

    public static Result classify(String packageName, String appMode, Notification notification, String combinedText) {
        String pkg = packageName == null ? "" : packageName.trim();
        String text = normalize(combinedText);
        String category = notification == null || notification.category == null ? "" : notification.category;

        boolean systemTrusted = AppTrustStore.MODE_SYSTEM_TRUSTED.equals(appMode);
        boolean trusted = AppTrustStore.MODE_TRUSTED.equals(appMode);
        boolean messaging = AppTrustStore.isMessagingPackage(pkg) || isMessageCategory(category);

        boolean playStore = "com.android.vending".equals(pkg);
        boolean playServices = "com.google.android.gms".equals(pkg) || "com.google.android.gsf".equals(pkg);
        boolean packageInstaller = "com.android.packageinstaller".equals(pkg)
                || "com.google.android.packageinstaller".equals(pkg)
                || "com.google.android.packageinstaller.permission.ui".equals(pkg);
        boolean systemUi = "com.android.systemui".equals(pkg);

        boolean updateText = containsAny(text,
                // PT
                "atualizacao", "atualizacoes", "atualizar", "atualizado", "atualizada", "atualizadas",
                "a atualizar", "instalacao", "a instalar", "instalado", "instalada", "transferencia concluida",
                "download concluido", "aplicacoes atualizadas", "apps atualizadas", "atualizacao disponivel",
                // EN
                "update available", "updates available", "system update", "google play system update", "security update",
                "update complete", "new version available", "updating", "updated", "app updated", "apps updated",
                "installing", "installed", "installation complete", "download complete", "pending update",
                // ES
                "actualizacion", "actualizaciones", "actualizar", "actualizando", "instalando", "instalado",
                // FR
                "mise a jour", "mettre a jour", "installation", "telechargement termine",
                // DE
                "aktualisierung", "aktualisieren", "wird aktualisiert", "installiert", "installation");

        boolean playProtectText = containsAny(text,
                "play protect", "verificacao de seguranca", "verificacao de apps", "app scan",
                "apps verificadas", "nenhuma app prejudicial", "no harmful apps found");

        boolean operationalText = updateText || containsAny(text,
                "sincronizacao concluida", "sincronizado", "sync complete", "synced",
                "copia de seguranca concluida", "backup concluido", "backup complete",
                "download concluido", "download complete", "transferencia concluida",
                "operacao concluida", "operation complete", "concluido com sucesso", "completed successfully");

        boolean progressOrSystemCategory = "progress".equals(category)
                || "status".equals(category)
                || "service".equals(category)
                || "system".equals(category);

        // Atualizações/instalações originadas pelo ecossistema oficial Android/Google.
        if ((playStore || packageInstaller || playServices || systemUi) && updateText) {
            return new Result(true, true,
                    "🟢 ATUALIZAÇÃO SEGURA",
                    playStore ? "Atualização reconhecida como proveniente da Google Play Store."
                            : packageInstaller ? "Instalação/atualização reconhecida pelo instalador oficial do Android."
                            : "Atualização/estado reconhecido por componente oficial do Android/Google.");
        }

        if ((playStore || playServices) && playProtectText) {
            return new Result(true, false,
                    "🟢 GOOGLE PLAY / PLAY PROTECT",
                    "Notificação de segurança/manutenção reconhecida como proveniente de componente oficial Google.");
        }

        // Apps marcadas pelo utilizador como CONFIÁVEIS podem ter notificações de
        // atualização/estado benignas. Nunca aplicamos esta exceção a apps de mensagens.
        if (trusted && !messaging && operationalText && !hasHighRiskPayload(text)) {
            return new Result(true, updateText,
                    updateText ? "🟢 ATUALIZAÇÃO DE APP CONFIÁVEL" : "🟢 SOLICITAÇÃO SEGURA",
                    updateText
                            ? "Atualização/instalação reconhecida numa app marcada como confiável pelo utilizador."
                            : "Notificação operacional reconhecida numa app marcada como confiável pelo utilizador.");
        }

        // Para componentes SISTEMA/CONFIÁVEL, estados operacionais sem conteúdo
        // sensível também são explicitamente classificados como seguros.
        if (systemTrusted && !messaging && (operationalText || progressOrSystemCategory) && !hasHighRiskPayload(text)) {
            return new Result(true, updateText,
                    updateText ? "🟢 ATUALIZAÇÃO SEGURA" : "🟢 SISTEMA / OPERAÇÃO SEGURA",
                    "Notificação operacional de componente classificado como sistema/confiável.");
        }

        return Result.none();
    }

    private static boolean isMessageCategory(String category) {
        return "msg".equals(category)
                || "email".equals(category)
                || "social".equals(category)
                || "call".equals(category);
    }

    private static boolean hasHighRiskPayload(String text) {
        if (text == null || text.isEmpty()) return false;
        return containsAny(text,
                "http://", "https://", "www.", "bit.ly", "tinyurl", "cutt.ly",
                "codigo sms", "codigo de verificacao", "otp", "pin", "password", "palavra passe",
                "envia dinheiro", "enviar dinheiro", "transferir agora", "mbway", "mb way",
                "iban", "acesso remoto", "anydesk", "teamviewer");
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null || text.isEmpty()) return false;
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
