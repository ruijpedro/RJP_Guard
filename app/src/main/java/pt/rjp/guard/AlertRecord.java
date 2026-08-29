package pt.rjp.guard;

import org.json.JSONException;
import org.json.JSONObject;

public final class AlertRecord {
    public final long timestamp;
    public final String channel;
    public final String sender;
    public final String number;
    public final String app;
    public final String level;
    public final String reason;
    public final String source;
    public final String action;
    public final String snippet;

    public AlertRecord(long timestamp, String channel, String sender, String number, String app,
                       String level, String reason, String source, String action, String snippet) {
        this.timestamp = timestamp;
        this.channel = clean(channel);
        this.sender = clean(sender);
        this.number = clean(number);
        this.app = clean(app);
        this.level = clean(level);
        this.reason = clean(reason);
        this.source = clean(source);
        this.action = clean(action);
        this.snippet = clean(snippet);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("timestamp", timestamp);
        o.put("channel", channel);
        o.put("sender", sender);
        o.put("number", number);
        o.put("app", app);
        o.put("level", level);
        o.put("reason", reason);
        o.put("source", source);
        o.put("action", action);
        o.put("snippet", snippet);
        return o;
    }

    public static AlertRecord fromJson(JSONObject o) {
        return new AlertRecord(
                o.optLong("timestamp", System.currentTimeMillis()),
                o.optString("channel", ""),
                o.optString("sender", ""),
                o.optString("number", ""),
                o.optString("app", ""),
                o.optString("level", ""),
                o.optString("reason", ""),
                o.optString("source", ""),
                o.optString("action", ""),
                o.optString("snippet", "")
        );
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
