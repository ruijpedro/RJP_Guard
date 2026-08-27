package pt.rjp.guard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GuardCallScreeningService extends CallScreeningService {
    @Override public void onScreenCall(Call.Details callDetails) {
        Uri handle = callDetails.getHandle();
        String number = handle != null ? handle.getSchemeSpecificPart() : "";
        boolean contact = isInContacts(number);
        ReputationEngine.Result result = ReputationEngine.lookup(this, number, contact);

        if (result.verdict == ReputationEngine.Verdict.SAFE) { allow(callDetails); return; }

        if (result.verdict == ReputationEngine.Verdict.BLOCKED) {
            block(callDetails);
            GuardNotifier.show(this, "🔴 CHAMADA BLOQUEADA", result.detail + " • Fonte: " + result.source, true);
            addHistory("CHAMADA BLOQUEADA — " + result.source, number);
            return;
        }

        if (result.verdict == ReputationEngine.Verdict.SCAM) {
            if (GuardStore.autoBlockPublic(this)) {
                block(callDetails);
                GuardNotifier.show(this, "🔴 POSSÍVEL BURLA — BLOQUEADA", result.detail + " • Fonte: " + result.source, true);
                addHistory("POSSÍVEL BURLA — " + result.source + " — bloqueada", number);
            } else {
                allow(callDetails);
                GuardNotifier.show(this, "🔴 POSSÍVEL BURLA", result.detail + ". Confirme a identidade antes de partilhar dados, códigos ou dinheiro.", true);
                addHistory("POSSÍVEL BURLA — " + result.source, number);
            }
            return;
        }

        allow(callDetails);
        GuardNotifier.show(this, result.title,
                result.detail + " Não partilhe códigos, dados bancários nem faça transferências por pedido inesperado.", false);
        addHistory("ATENÇÃO — " + result.source, number);
    }

    private void allow(Call.Details details) {
        respondToCall(details, new CallResponse.Builder().setDisallowCall(false).setRejectCall(false).setSilenceCall(false).setSkipCallLog(false).setSkipNotification(false).build());
    }

    private void block(Call.Details details) {
        respondToCall(details, new CallResponse.Builder().setDisallowCall(true).setRejectCall(true).setSilenceCall(true).setSkipCallLog(false).setSkipNotification(false).build());
    }

    private boolean isInContacts(String number) {
        if (number == null || number.isEmpty()) return false;
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cursor = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID}, null, null, null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (Exception ignored) { return false; }
    }

    private void addHistory(String label, String number) {
        String time = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date());
        GuardStore.addHistory(this, time + " — " + label + " — " + mask(number));
    }

    private String mask(String number) {
        String n = SpamDatabase.canonical(number); if (n.length() <= 4) return n;
        return "•••••" + n.substring(n.length()-4);
    }
}
