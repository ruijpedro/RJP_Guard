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

    @Override
    public void onScreenCall(Call.Details callDetails) {
        Uri handle = callDetails.getHandle();
        String number = handle != null ? handle.getSchemeSpecificPart() : "";

        boolean blocked = GuardStore.isBlocked(this, number);
        boolean safe = GuardStore.isSafe(this, number) || isInContacts(number);

        CallResponse response;
        if (blocked) {
            response = new CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSilenceCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build();
            respondToCall(callDetails, response);
            GuardNotifier.show(this, "🔴 CHAMADA BLOQUEADA", "Número marcado como bloqueado no RJP Guard.", true);
            addHistory("CHAMADA BLOQUEADA", number);
            return;
        }

        response = new CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build();
        respondToCall(callDetails, response);

        if (!safe) {
            GuardNotifier.show(this, "🟡 ATENÇÃO • NÚMERO DESCONHECIDO",
                    "A chamada foi permitida. Não partilhe códigos, dados bancários ou faça transferências por pedido do interlocutor.", false);
            addHistory("ATENÇÃO — número desconhecido", number);
        }
    }

    private boolean isInContacts(String number) {
        if (number == null || number.isEmpty()) return false;
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup._ID}, null, null, null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void addHistory(String label, String number) {
        String time = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date());
        GuardStore.addHistory(this, time + " — " + label + " — " + mask(number));
    }

    private String mask(String number) {
        String n = GuardStore.normalizeNumber(number);
        if (n.length() <= 4) return n;
        return "•••••" + n.substring(n.length() - 4);
    }
}
