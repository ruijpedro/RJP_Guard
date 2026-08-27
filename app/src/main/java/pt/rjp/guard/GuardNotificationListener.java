package pt.rjp.guard;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GuardNotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (getPackageName().equals(sbn.getPackageName())) return;

        Notification n = sbn.getNotification();
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE, "");
        CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT, "");
        CharSequence big = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT, "");

        String combined = (title + " " + text + " " + big).trim();
        if (combined.length() < 8) return;

        RiskAnalyzer.Result result = RiskAnalyzer.analyze(combined);
        if (result.level == RiskAnalyzer.Level.SAFE) return;

        String appName = appLabel(sbn.getPackageName());
        String alertTitle = result.level == RiskAnalyzer.Level.SCAM ? "🔴 POSSÍVEL BURLA" : "🟡 ATENÇÃO";
        String warning = result.reason + " Não envie dinheiro, códigos ou dados pessoais.";

        GuardNotifier.show(this, alertTitle + " • " + appName, warning, result.level == RiskAnalyzer.Level.SCAM);

        String time = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date());
        GuardStore.addHistory(this, time + " — " + result.level.label + " — " + appName + " — " + result.reason);
    }

    private String appLabel(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }
}
