package pt.rjp.guard;

import android.Manifest;
import android.app.Notification;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuardNotificationListener extends NotificationListenerService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d .()\\-]{7,}\\d)(?!\\d)");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (getPackageName().equals(sbn.getPackageName())) return;

        Notification n = sbn.getNotification();
        String title = stringExtra(n, Notification.EXTRA_TITLE);
        String text = stringExtra(n, Notification.EXTRA_TEXT);
        String big = stringExtra(n, Notification.EXTRA_BIG_TEXT);
        String subText = stringExtra(n, Notification.EXTRA_SUB_TEXT);

        String combined = (title + " " + text + " " + big + " " + subText).trim();
        if (combined.length() < 8) return;

        RiskAnalyzer.Result result = RiskAnalyzer.analyze(combined);
        if (result.level == RiskAnalyzer.Level.SAFE) return;

        String appName = appLabel(sbn.getPackageName());
        String number = extractNumber(title);
        if (number.isEmpty()) number = extractNumber(text);
        if (number.isEmpty()) number = extractNumber(big);
        if (number.isEmpty()) number = numberForUniqueContactName(title);

        String sender = title.trim();
        if (sender.isEmpty()) sender = !number.isEmpty() ? number : "Remetente não identificado";

        String alertTitle = result.level == RiskAnalyzer.Level.SCAM ? "🔴 POSSÍVEL BURLA" : "🟡 ATENÇÃO";
        String warning = result.reason + " Não envie dinheiro, códigos ou dados pessoais.";
        GuardNotifier.show(this, alertTitle + " • " + appName, warning, result.level == RiskAnalyzer.Level.SCAM);

        String level = result.level == RiskAnalyzer.Level.SCAM ? "🔴 POSSÍVEL BURLA" : "🟡 ATENÇÃO";
        GuardStore.addAlert(this, new AlertRecord(
                System.currentTimeMillis(),
                "Mensagem / notificação",
                sender,
                number,
                appName,
                level,
                result.reason,
                "Análise local RJP Guard",
                "Alerta apresentado",
                snippet(text.isEmpty() ? big : text)
        ));
        GuardStore.addHistory(this, level + " — " + appName + " — " + sender + " — " + result.reason);
    }

    private String stringExtra(Notification n, String key) {
        CharSequence value = n.extras.getCharSequence(key, "");
        return value == null ? "" : value.toString().trim();
    }

    private String extractNumber(String value) {
        if (value == null || value.isEmpty()) return "";
        Matcher m = PHONE_PATTERN.matcher(value);
        while (m.find()) {
            String candidate = m.group();
            String canonical = SpamDatabase.canonical(candidate);
            if (canonical.length() >= 9 && canonical.length() <= 15) return candidate.trim();
        }
        return "";
    }


    private String numberForUniqueContactName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) return "";
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return "";

        String found = "";
        String selection = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " = ?";
        String[] args = {displayName.trim()};
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, selection, args, null)) {
            if (cursor == null) return "";
            while (cursor.moveToNext()) {
                String value = cursor.getString(0);
                if (value == null || value.trim().isEmpty()) continue;
                if (found.isEmpty()) found = value.trim();
                else if (!SpamDatabase.canonical(found).equals(SpamDatabase.canonical(value))) return "";
            }
        } catch (Exception ignored) { return ""; }
        return found;
    }

    private String snippet(String value) {
        if (value == null) return "";
        String s = value.replaceAll("\\s+", " ").trim();
        return s.length() <= 180 ? s : s.substring(0, 177) + "…";
    }

    private String appLabel(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }
}
