package pt.rjp.guard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private static final int ROLE_REQUEST = 1001;
    private static final int PERMISSION_REQUEST = 1002;

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GuardNotifier.ensureChannel(this);
        requestNeededPermissions();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 247, 251));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        scroll.removeAllViews();
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.rjp_guard_brand);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)));

        TextView protection = title("PROTEÇÃO DO TELEMÓVEL");
        protection.setGravity(Gravity.CENTER);
        root.addView(protection);

        root.addView(statusCard("Chamadas", isCallScreeningActive() ? "🟢 ATIVA" : "🟡 ATIVAR", isCallScreeningActive()));
        root.addView(statusCard("Mensagens / notificações", isNotificationAccessEnabled() ? "🟢 ATIVA" : "🟡 ATIVAR", isNotificationAccessEnabled()));

        Button callButton = actionButton("🛡️ ATIVAR PROTEÇÃO DE CHAMADAS");
        callButton.setOnClickListener(v -> requestCallScreeningRole());
        root.addView(callButton);

        Button notifButton = actionButton("💬 ATIVAR ANÁLISE DE MENSAGENS");
        notifButton.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        root.addView(notifButton);

        root.addView(sectionTitle("LISTAS DE PROTEÇÃO"));
        root.addView(infoText("Contactos da agenda são considerados seguros. Também podes adicionar números manualmente."));

        LinearLayout numberRow = new LinearLayout(this);
        numberRow.setOrientation(LinearLayout.VERTICAL);
        numberRow.setPadding(0, dp(4), 0, dp(4));

        EditText number = new EditText(this);
        number.setHint("Número de telefone");
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        number.setTextSize(18);
        number.setPadding(dp(14), dp(12), dp(14), dp(12));
        numberRow.addView(number, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button safe = actionButton("✓ ADICIONAR COMO SEGURO");
        safe.setOnClickListener(v -> {
            String n = number.getText().toString().trim();
            if (n.isEmpty()) return;
            GuardStore.addSafe(this, n);
            number.setText("");
            Toast.makeText(this, "Número adicionado como seguro", Toast.LENGTH_SHORT).show();
            render();
        });
        numberRow.addView(safe);

        Button block = dangerButton("⛔ BLOQUEAR NÚMERO");
        block.setOnClickListener(v -> {
            String n = number.getText().toString().trim();
            if (n.isEmpty()) return;
            GuardStore.addBlocked(this, n);
            number.setText("");
            Toast.makeText(this, "Número adicionado à lista de bloqueados", Toast.LENGTH_SHORT).show();
            render();
        });
        numberRow.addView(block);
        root.addView(numberRow);

        root.addView(infoCard("Números seguros", String.valueOf(GuardStore.safeCount(this))));
        root.addView(infoCard("Números bloqueados", String.valueOf(GuardStore.blockedCount(this))));

        root.addView(sectionTitle("TESTE RÁPIDO"));
        Button test = actionButton("TESTAR MENSAGEM DE BURLA");
        test.setOnClickListener(v -> testAnalyzer());
        root.addView(test);

        root.addView(sectionTitle("ÚLTIMOS ALERTAS"));
        List<String> history = GuardStore.getHistory(this);
        if (history.isEmpty()) {
            root.addView(infoText("Ainda não existem alertas."));
        } else {
            int max = Math.min(10, history.size());
            for (int i = 0; i < max; i++) root.addView(historyItem(history.get(i)));
        }

        root.addView(sectionTitle("PRIVACIDADE"));
        root.addView(infoText("A análise é feita localmente no telemóvel. A V1 não envia mensagens, contactos ou chamadas para um servidor."));
        root.addView(infoText("RJP Guard é uma ajuda preventiva. Nunca substitui a confirmação direta com familiares, bancos ou autoridades."));

        setContentView(scroll);
    }

    private void testAnalyzer() {
        String sample = "Olá pai, perdi o telemóvel. Este é o meu novo número. Preciso que faças uma transferência urgente por MB WAY.";
        RiskAnalyzer.Result result = RiskAnalyzer.analyze(sample);
        String guidance = result.level == RiskAnalyzer.Level.SCAM
                ? "\n\nNÃO RESPONDA • NÃO ENVIE DINHEIRO • NÃO PARTILHE CÓDIGOS"
                : "";
        new AlertDialog.Builder(this)
                .setTitle(result.level == RiskAnalyzer.Level.SCAM ? "🔴 POSSÍVEL BURLA" : result.level.label)
                .setMessage("Mensagem de teste:\n\n\"" + sample + "\"\n\n" + result.reason + guidance)
                .setPositiveButton("OK", null)
                .show();
    }

    private void requestNeededPermissions() {
        java.util.ArrayList<String> needed = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST);
    }

    private boolean isCallScreeningActive() {
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        return rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private void requestCallScreeningRole() {
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Este Android não disponibiliza a função de filtragem de chamadas.", Toast.LENGTH_LONG).show();
            return;
        }
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "A proteção de chamadas já está ativa.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), ROLE_REQUEST);
    }

    private boolean isNotificationAccessEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, GuardNotificationListener.class);
        return enabled.contains(me.flattenToString()) || enabled.contains(getPackageName());
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(Color.rgb(7, 27, 58));
        t.setPadding(0, dp(8), 0, dp(10));
        return t;
    }

    private TextView sectionTitle(String text) {
        TextView t = title(text);
        t.setPadding(0, dp(22), 0, dp(8));
        return t;
    }

    private TextView infoText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(Color.rgb(71, 84, 103));
        t.setLineSpacing(0, 1.15f);
        t.setPadding(dp(4), dp(6), dp(4), dp(10));
        return t;
    }

    private View statusCard(String label, String state, boolean active) {
        LinearLayout row = card();
        TextView left = new TextView(this);
        left.setText(label);
        left.setTextSize(17);
        left.setTextColor(Color.rgb(16, 24, 40));
        left.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView right = new TextView(this);
        right.setText(state);
        right.setTextSize(16);
        right.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        right.setTextColor(active ? Color.rgb(21, 122, 67) : Color.rgb(166, 99, 0));
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(right);
        return row;
    }

    private View infoCard(String label, String value) {
        LinearLayout row = card();
        TextView left = new TextView(this);
        left.setText(label);
        left.setTextSize(16);
        left.setTextColor(Color.rgb(16, 24, 40));
        TextView right = new TextView(this);
        right.setText(value);
        right.setTextSize(20);
        right.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        right.setTextColor(Color.rgb(10, 102, 216));
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(right);
        return row;
    }

    private LinearLayout card() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        row.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        row.setLayoutParams(lp);
        row.setElevation(dp(2));
        return row;
    }

    private TextView historyItem(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(Color.rgb(16, 24, 40));
        t.setBackgroundColor(Color.WHITE);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        t.setLayoutParams(lp);
        return t;
    }

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackgroundColor(Color.rgb(10, 102, 216));
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private Button dangerButton(String text) {
        Button b = actionButton(text);
        b.setBackgroundColor(Color.rgb(179, 38, 30));
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
