package pt.rjp.guard;

import android.content.Context;

public final class MultiSourceSyncManager {
    public interface Callback { void onFinished(String message); }
    private MultiSourceSyncManager() {}

    public static void autoSyncIfNeeded(Context context) {
        SpamSyncManager.autoSyncIfNeeded(context, null);
        CallShieldTrendSyncManager.autoSyncIfNeeded(context);
        FtcDncSyncManager.autoSyncIfNeeded(context, null);
        CommunitySyncManager.autoSyncIfNeeded(context, null);
    }

    public static void syncAll(Context context, Callback callback) {
        SpamSyncManager.sync(context, (ok1,msg1,count1) ->
                CallShieldTrendSyncManager.sync(context, (okT,msgT) ->
                        FtcDncSyncManager.sync(context, (ok2,msg2,count2) ->
                                CommunitySyncManager.sync(context, (ok3,msg3,count3) -> {
                                    String message = msg1 + " • " + msgT + " • " + msg2 + " • " + msg3 + " • total local: " + SpamDatabase.get(context).count();
                                    if (callback != null) callback.onFinished(message);
                                }))));
    }
}
