package pt.rjp.guard;

import android.content.Context;

public final class MultiSourceSyncManager {
    public interface Callback { void onFinished(String message); }
    private MultiSourceSyncManager() {}

    public static void autoSyncIfNeeded(Context context) {
        SpamSyncManager.autoSyncIfNeeded(context, null);
        FtcDncSyncManager.autoSyncIfNeeded(context, null);
    }

    public static void syncAll(Context context, Callback callback) {
        SpamSyncManager.sync(context, (ok1,msg1,count1) ->
                FtcDncSyncManager.sync(context, (ok2,msg2,count2) -> {
                    String message = msg1 + " • " + msg2 + " • total local: " + SpamDatabase.get(context).count();
                    if (callback != null) callback.onFinished(message);
                }));
    }
}
