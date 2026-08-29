package pt.rjp.guard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallScreeningService;

public class GuardCallScreeningService extends CallScreeningService {
    @Override public void onScreenCall(Call.Details callDetails) {
        Uri handle = callDetails.getHandle();
        String number = handle != null ? handle.getSchemeSpecificPart() : "";
        String contactName = contactName(number);
        boolean contact = !contactName.isEmpty();
        ReputationEngine.Result result = ReputationEngine.lookup(this, number, contact);

        if (result.verdict == ReputationEngine.Verdict.SAFE) {
            allow(callDetails);
            return;
        }

        if (result.verdict == ReputationEngine.Verdict.BLOCKED) {
            block(callDetails);
            GuardNotifier.show(this, "🔴 CHAMADA BLOQUEADA", result.detail + " • Fonte: " + result.source, true);
            saveAlert(number, contactName, "🔴 BLOQUEADA", result.detail, result.source, "Bloqueada");
            return;
        }

        if (result.verdict == ReputationEngine.Verdict.SCAM) {
            if (GuardStore.autoBlockPublic(this)) {
                block(callDetails);
                GuardNotifier.show(this, "🔴 POSSÍVEL BURLA — BLOQUEADA", result.detail + " • Fonte: " + result.source, true);
                saveAlert(number, contactName, "🔴 POSSÍVEL BURLA", result.detail, result.source, "Bloqueada automaticamente");
            } else {
                allow(callDetails);
                GuardNotifier.show(this, "🔴 POSSÍVEL BURLA", result.detail + ". Confirme a identidade antes de partilhar dados, códigos ou dinheiro.", true);
                saveAlert(number, contactName, "🔴 POSSÍVEL BURLA", result.detail, result.source, "Chamada permitida com alerta");
            }
            return;
        }

        allow(callDetails);
        GuardNotifier.show(this, result.title,
                result.detail + " Não partilhe códigos, dados bancários nem faça transferências por pedido inesperado.", false);
        saveAlert(number, contactName, "🟡 ATENÇÃO", result.detail, result.source, "Chamada permitida com alerta");
    }

    private void allow(Call.Details details) {
        respondToCall(details, new CallResponse.Builder()
                .setDisallowCall(false).setRejectCall(false).setSilenceCall(false)
                .setSkipCallLog(false).setSkipNotification(false).build());
    }

    private void block(Call.Details details) {
        respondToCall(details, new CallResponse.Builder()
                .setDisallowCall(true).setRejectCall(true).setSilenceCall(true)
                .setSkipCallLog(false).setSkipNotification(false).build());
    }

    private String contactName(String number) {
        if (number == null || number.isEmpty()) return "";
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return "";
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String[] projection = {ContactsContract.PhoneLookup.DISPLAY_NAME};
        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    return name == null ? "" : name.trim();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void saveAlert(String number, String contactName, String level, String reason, String source, String action) {
        String cleanNumber = number == null ? "" : number.trim();
        String sender = !contactName.isEmpty() ? contactName : (cleanNumber.isEmpty() ? "Número não identificado" : cleanNumber);
        GuardStore.addAlert(this, new AlertRecord(
                System.currentTimeMillis(),
                "Chamada",
                sender,
                cleanNumber,
                "Telefone",
                level,
                reason,
                source,
                action,
                ""
        ));
        GuardStore.addHistory(this, level + " — " + sender + " — " + source);
    }
}
