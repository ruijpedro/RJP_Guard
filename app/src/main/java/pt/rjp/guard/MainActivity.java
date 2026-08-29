package pt.rjp.guard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int ROLE_REQUEST = 1001;
    private static final int PERMISSION_REQUEST = 1002;
    private static final String[] REPORT_CATEGORIES = {
            "Burla", "Telemarketing", "Falso banco", "Falso familiar", "Encomenda / CTT",
            "Energia / telecomunicações", "Chamada automática", "Outro"
    };

    private LinearLayout root;
    private boolean initialSyncRequested = false;
    private String pendingSharedText = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GuardNotifier.ensureChannel(this);
        requestNeededPermissions();
        readSharedText(getIntent());
        render();
        requestInitialSpamSync();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readSharedText(intent);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (root != null) render();
    }

    private void readSharedText(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) pendingSharedText = text.trim();
        }
    }

    private void requestInitialSpamSync() {
        if (initialSyncRequested) return;
        initialSyncRequested = true;
        MultiSourceSyncManager.autoSyncIfNeeded(this);
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 247, 251));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.rjp_guard_brand);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(172)));

        TextView version = infoText("RJP Guard V3.3 • Proteção local com múltiplas fontes nacionais e internacionais");
        version.setGravity(Gravity.CENTER);
        root.addView(version);

        root.addView(sectionTitle("PROTEÇÃO DO TELEMÓVEL"));
        root.addView(statusCard("Chamadas", isCallScreeningActive() ? "🟢 ATIVA" : "🟡 ATIVAR", isCallScreeningActive()));
        root.addView(statusCard("Mensagens / notificações", isNotificationAccessEnabled() ? "🟢 ATIVA" : "🟡 ATIVAR", isNotificationAccessEnabled()));

        Button callButton = actionButton("🛡️ ATIVAR PROTEÇÃO DE CHAMADAS");
        callButton.setOnClickListener(v -> requestCallScreeningRole());
        root.addView(callButton);

        Button notifButton = actionButton("💬 ATIVAR ANÁLISE DE MENSAGENS");
        notifButton.setOnClickListener(v -> safeStartActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        root.addView(notifButton);

        root.addView(sectionTitle("FONTES DE PROTEÇÃO"));
        SpamDatabase spamDb = SpamDatabase.get(this);
        long dbCount = spamDb.count();
        long callShieldCount = spamDb.countBySource("CallShield");
        long ftcCount = spamDb.countBySource("FTC DNC");
        String callShieldLast = spamDb.meta("callshield_last_sync", spamDb.meta("last_sync", "Ainda não atualizada"));
        String callShieldVersion = spamDb.meta("callshield_version", spamDb.meta("version", ""));
        String ftcLast = spamDb.meta("ftc_last_sync", "Ainda não atualizada");
        String ftcDate = spamDb.meta("ftc_source_date", "");

        root.addView(sourceCard("🇵🇹 ANACOM", "Plano Nacional de Numeração", "Fonte oficial • " + PortugalNumbering.SOURCE_UPDATED, true));
        root.addView(infoText("A ANACOM valida e classifica gamas nacionais. Não é uma blacklist de burlas e por isso nunca é usada isoladamente para marcar fraude."));
        Button anacom = secondaryButton("ABRIR PESQUISA OFICIAL ANACOM");
        anacom.setOnClickListener(v -> safeStartActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PortugalNumbering.SOURCE_URL))));
        root.addView(anacom);

        root.addView(sourceCard("🛡️ CallShield", "Base internacional agregada", callShieldCount + " números • " + callShieldLast, callShieldCount > 0));
        if (!callShieldVersion.isEmpty()) root.addView(infoText("Versão CallShield: " + callShieldVersion + ". A CallShield agrega várias fontes públicas/comunitárias e continua a ser a principal fonte de reputação do RJP Guard."));

        root.addView(sourceCard("🇺🇸 FTC Do Not Call", "Base oficial de chamadas indesejadas/robocalls", ftcCount + " números • " + ftcLast + (ftcDate.isEmpty()?"":" • ficheiro "+ftcDate), ftcCount > 0));
        root.addView(infoText("A FTC publica relatos de consumidores. Estes registos são úteis como sinal adicional, mas não são verificados pela FTC e não significam automaticamente burla."));
        Button ftcPage = secondaryButton("ABRIR FONTE OFICIAL FTC");
        ftcPage.setOnClickListener(v -> safeStartActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FtcDncSyncManager.SOURCE_PAGE))));
        root.addView(ftcPage);

        root.addView(sourceCard("🇵🇹 tellows Portugal", "Reputação portuguesa", "Preparada • requer API/licença oficial", false));
        root.addView(infoText("A integração está preparada, mas fica desativada até existir uma chave/licença válida. O RJP Guard não faz scraping de bases nacionais."));

        root.addView(infoCard("Números únicos na base local", String.valueOf(dbCount)));
        root.addView(infoText("As bases são descarregadas por HTTPS e consultadas localmente. Durante uma chamada não é feita uma pesquisa online do número."));

        Button updateDb = actionButton("↻ ATUALIZAR TODAS AS FONTES");
        updateDb.setOnClickListener(v -> {
            updateDb.setEnabled(false); updateDb.setText("A atualizar fontes…");
            MultiSourceSyncManager.syncAll(this, message -> {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                render();
            });
        });
        root.addView(updateDb);

        boolean autoBlock = GuardStore.autoBlockPublic(this);
        Button autoBlockButton = autoBlock ? dangerButton("⛔ BASES PÚBLICAS: BLOQUEIO AUTOMÁTICO ATIVO") : secondaryButton("🔔 BASES PÚBLICAS: SÓ AVISAR");
        autoBlockButton.setOnClickListener(v -> { GuardStore.setAutoBlockPublic(this, !GuardStore.autoBlockPublic(this)); render(); });
        root.addView(autoBlockButton);
        root.addView(infoText(autoBlock
                ? "Números classificados como POSSÍVEL BURLA pelas fontes de reputação são bloqueados, exceto contactos e números seguros."
                : "Modo conservador: fontes públicas geram aviso; só sinais fortes como CallShield são tratados como POSSÍVEL BURLA. Relatos FTC isolados não são bloqueados automaticamente."));

        root.addView(sectionTitle("VERIFICAR NÚMERO"));
        EditText lookup = phoneInput("Ex.: +351 912 345 678");
        root.addView(lookup);
        Button lookupButton = actionButton("🔎 VERIFICAR NÚMERO");
        lookupButton.setOnClickListener(v -> lookupNumber(lookup.getText().toString().trim()));
        root.addView(lookupButton);

        root.addView(sectionTitle("LISTAS RJP GUARD"));
        root.addView(infoText("Contactos da agenda têm prioridade. Podes ainda marcar um número como seguro ou reportá-lo localmente."));
        EditText number = phoneInput("Número de telefone");
        root.addView(number);

        Button safe = actionButton("✓ ADICIONAR COMO SEGURO");
        safe.setOnClickListener(v -> {
            String n = number.getText().toString().trim(); if (n.isEmpty()) return;
            GuardStore.addSafe(this, n); number.setText("");
            Toast.makeText(this, "Número adicionado como seguro", Toast.LENGTH_SHORT).show(); render();
        });
        root.addView(safe);

        Button report = dangerButton("⚠ REPORTAR / BLOQUEAR NÚMERO");
        report.setOnClickListener(v -> {
            String n = number.getText().toString().trim();
            if (n.isEmpty()) { Toast.makeText(this, "Introduz primeiro o número.", Toast.LENGTH_SHORT).show(); return; }
            chooseReportCategory(n, number);
        });
        root.addView(report);
        root.addView(infoCard("Números seguros", String.valueOf(GuardStore.safeCount(this))));
        root.addView(infoCard("Bloqueados / reportados", String.valueOf(GuardStore.blockedCount(this))));

        root.addView(sectionTitle("VERIFICAR MENSAGEM OU LINK"));
        EditText message = multilineInput("Cola aqui SMS, WhatsApp, link ou texto suspeito");
        if (!pendingSharedText.isEmpty()) message.setText(pendingSharedText);
        root.addView(message);
        Button analyze = actionButton("🛡️ ANALISAR MENSAGEM");
        analyze.setOnClickListener(v -> analyzeMessage(message.getText().toString()));
        root.addView(analyze);
        root.addView(infoText("Também podes usar Partilhar → RJP Guard a partir de outra app para analisar o texto localmente."));

        if (!pendingSharedText.isEmpty()) {
            analyzeMessage(pendingSharedText);
            pendingSharedText = "";
        }

        root.addView(sectionTitle("HISTÓRICO DE ALERTAS"));
        root.addView(infoText("Os alertas guardam localmente quem contactou: número quando disponível, remetente, app, data/hora e motivo. Toca num alerta para ver os detalhes."));
        List<AlertRecord> alerts = GuardStore.getAlerts(this);
        if (alerts.isEmpty()) {
            root.addView(infoText("Ainda não existem alertas identificados nesta versão."));
            List<String> legacyHistory = GuardStore.getHistory(this);
            if (!legacyHistory.isEmpty()) {
                root.addView(infoText("Existem registos de versões anteriores, mas esses registos não guardavam o número completo e não é possível recuperá-lo retroativamente."));
            }
        } else {
            for (int i=0;i<Math.min(30, alerts.size());i++) root.addView(alertItem(alerts.get(i)));
            Button clearAlerts = dangerButton("🗑 APAGAR HISTÓRICO DE ALERTAS");
            clearAlerts.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Apagar histórico?")
                    .setMessage("Serão apagados os alertas guardados neste telemóvel. As listas de números seguros e bloqueados não serão alteradas.")
                    .setPositiveButton("Apagar", (d, w) -> { GuardStore.clearAlerts(this); render(); })
                    .setNegativeButton("Cancelar", null)
                    .show());
            root.addView(clearAlerts);
        }

        root.addView(sectionTitle("PRIVACIDADE"));
        root.addView(infoText("Chamadas e mensagens são analisadas no próprio telemóvel. A rede é usada apenas para atualizar fontes públicas configuradas, como a CallShield."));
        root.addView(infoText("A ANACOM classifica numeração; não certifica que uma chamada seja legítima. O caller ID pode ser falsificado (spoofing), por isso confirma pedidos sensíveis por um canal conhecido."));

        setContentView(scroll);
    }

    private void chooseReportCategory(String number, EditText field) {
        new AlertDialog.Builder(this)
                .setTitle("Categoria do reporte")
                .setItems(REPORT_CATEGORIES, (dialog, which) -> {
                    GuardStore.reportLocal(this, number, REPORT_CATEGORIES[which]);
                    field.setText("");
                    Toast.makeText(this, "Número reportado e bloqueado: " + REPORT_CATEGORIES[which], Toast.LENGTH_LONG).show();
                    render();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void lookupNumber(String number) {
        if (number.isEmpty()) { Toast.makeText(this, "Introduz um número.", Toast.LENGTH_SHORT).show(); return; }
        ReputationEngine.Result r = ReputationEngine.lookup(this, number, false);
        StringBuilder msg = new StringBuilder();
        msg.append(r.detail).append("\n\nFonte: ").append(r.source);
        if (r.numbering != null && r.numbering.portuguese) {
            msg.append("\nANACOM/PNN: ").append(r.numbering.category);
        }
        if (r.verdict != ReputationEngine.Verdict.SAFE) {
            msg.append("\n\nNão partilhe códigos, dados bancários ou dinheiro apenas com base no número apresentado.");
        }
        resultDialog(r.title, msg.toString());
    }

    private void analyzeMessage(String text) {
        if (text == null || text.trim().isEmpty()) { Toast.makeText(this, "Cola ou escreve uma mensagem.", Toast.LENGTH_SHORT).show(); return; }
        RiskAnalyzer.Result result = RiskAnalyzer.analyze(text);
        String title = result.level == RiskAnalyzer.Level.SCAM ? "🔴 POSSÍVEL BURLA" : result.level == RiskAnalyzer.Level.WARNING ? "🟡 ATENÇÃO" : "🟢 SEGURO";
        String guidance = result.level == RiskAnalyzer.Level.SCAM
                ? "\n\nNÃO RESPONDA • NÃO ENVIE DINHEIRO • NÃO PARTILHE CÓDIGOS"
                : result.level == RiskAnalyzer.Level.WARNING ? "\n\nConfirme o remetente por um canal conhecido antes de agir." : "";
        resultDialog(title, result.reason + guidance + "\n\nAnálise feita localmente pelo RJP Guard.");
    }

    private void resultDialog(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
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
            Toast.makeText(this, "Este Android não disponibiliza a função de filtragem de chamadas.", Toast.LENGTH_LONG).show(); return;
        }
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "A proteção de chamadas já está ativa.", Toast.LENGTH_SHORT).show(); return;
        }
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), ROLE_REQUEST);
    }

    private boolean isNotificationAccessEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, GuardNotificationListener.class);
        return enabled.contains(me.flattenToString()) || enabled.contains(getPackageName());
    }

    private void safeStartActivity(Intent intent) {
        try { startActivity(intent); } catch (Exception e) { Toast.makeText(this, "Não foi possível abrir esta opção neste dispositivo.", Toast.LENGTH_LONG).show(); }
    }

    private EditText phoneInput(String hint) {
        EditText input = new EditText(this); input.setHint(hint); input.setInputType(InputType.TYPE_CLASS_PHONE); input.setTextSize(18); input.setPadding(dp(14),dp(12),dp(14),dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,dp(4),0,dp(4)); input.setLayoutParams(lp); return input;
    }

    private EditText multilineInput(String hint) {
        EditText input = new EditText(this); input.setHint(hint); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.setMinLines(4); input.setGravity(Gravity.TOP); input.setTextSize(16); input.setPadding(dp(14),dp(12),dp(14),dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130)); lp.setMargins(0,dp(4),0,dp(4)); input.setLayoutParams(lp); return input;
    }

    private TextView title(String text) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(17); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setTextColor(Color.rgb(7,27,58)); t.setPadding(0,dp(8),0,dp(10)); return t;
    }
    private TextView sectionTitle(String text) { TextView t=title(text); t.setPadding(0,dp(22),0,dp(8)); return t; }
    private TextView infoText(String text) { TextView t=new TextView(this); t.setText(text); t.setTextSize(14); t.setTextColor(Color.rgb(71,84,103)); t.setLineSpacing(0,1.15f); t.setPadding(dp(4),dp(5),dp(4),dp(9)); return t; }

    private View statusCard(String label, String state, boolean active) {
        LinearLayout row=card(); TextView left=new TextView(this); left.setText(label); left.setTextSize(17); left.setTextColor(Color.rgb(16,24,40)); left.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        TextView right=new TextView(this); right.setText(state); right.setTextSize(16); right.setTypeface(Typeface.DEFAULT,Typeface.BOLD); right.setTextColor(active?Color.rgb(21,122,67):Color.rgb(166,99,0));
        row.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); row.addView(right); return row;
    }

    private View sourceCard(String source, String label, String state, boolean active) {
        LinearLayout outer = card(); outer.setOrientation(LinearLayout.VERTICAL); outer.setGravity(Gravity.START);
        TextView top=new TextView(this); top.setText(source); top.setTextSize(18); top.setTypeface(Typeface.DEFAULT,Typeface.BOLD); top.setTextColor(Color.rgb(7,27,58));
        TextView middle=new TextView(this); middle.setText(label); middle.setTextSize(15); middle.setTextColor(Color.rgb(52,64,84));
        TextView bottom=new TextView(this); bottom.setText((active?"🟢 ":"🟡 ")+state); bottom.setTextSize(14); bottom.setTypeface(Typeface.DEFAULT,Typeface.BOLD); bottom.setTextColor(active?Color.rgb(21,122,67):Color.rgb(166,99,0)); bottom.setPadding(0,dp(6),0,0);
        outer.addView(top); outer.addView(middle); outer.addView(bottom); return outer;
    }

    private View infoCard(String label, String value) {
        LinearLayout row=card(); TextView left=new TextView(this); left.setText(label); left.setTextSize(16); left.setTextColor(Color.rgb(16,24,40)); TextView right=new TextView(this); right.setText(value); right.setTextSize(20); right.setTypeface(Typeface.DEFAULT,Typeface.BOLD); right.setTextColor(Color.rgb(10,102,216)); row.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); row.addView(right); return row;
    }

    private LinearLayout card() {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16),dp(15),dp(16),dp(15)); row.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,dp(5),0,dp(5)); row.setLayoutParams(lp); row.setElevation(dp(2)); return row;
    }

    private View alertItem(AlertRecord alert) {
        LinearLayout box = card();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.START);

        String when = new SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()).format(new Date(alert.timestamp));
        TextView top = new TextView(this);
        top.setText(alert.level + "   " + alert.channel);
        top.setTextSize(16);
        top.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.setTextColor(alert.level.contains("🔴") ? Color.rgb(179,38,30) : Color.rgb(166,99,0));

        String who = !alert.number.isEmpty() ? alert.number : alert.sender;
        TextView contact = new TextView(this);
        contact.setText("📞 " + (who.isEmpty() ? "Remetente não identificado" : who));
        contact.setTextSize(18);
        contact.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        contact.setTextColor(Color.rgb(7,27,58));
        contact.setPadding(0,dp(6),0,0);

        TextView meta = new TextView(this);
        StringBuilder metaText = new StringBuilder(when);
        if (!alert.sender.isEmpty() && !alert.sender.equals(who)) metaText.append(" • ").append(alert.sender);
        if (!alert.app.isEmpty()) metaText.append(" • ").append(alert.app);
        meta.setText(metaText.toString());
        meta.setTextSize(13);
        meta.setTextColor(Color.rgb(71,84,103));
        meta.setPadding(0,dp(4),0,0);

        TextView reason = new TextView(this);
        reason.setText(alert.reason);
        reason.setTextSize(14);
        reason.setTextColor(Color.rgb(52,64,84));
        reason.setPadding(0,dp(7),0,0);

        box.addView(top);
        box.addView(contact);
        box.addView(meta);
        if (!alert.reason.isEmpty()) box.addView(reason);
        box.setClickable(true);
        box.setOnClickListener(v -> showAlertDetails(alert));
        return box;
    }

    private void showAlertDetails(AlertRecord alert) {
        String when = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale.getDefault()).format(new Date(alert.timestamp));
        StringBuilder msg = new StringBuilder();
        msg.append("Data/hora: ").append(when);
        msg.append("\nTipo: ").append(alert.channel);
        if (!alert.number.isEmpty()) msg.append("\nNúmero: ").append(alert.number);
        if (!alert.sender.isEmpty()) msg.append("\nRemetente: ").append(alert.sender);
        if (!alert.app.isEmpty()) msg.append("\nApp/origem: ").append(alert.app);
        if (!alert.source.isEmpty()) msg.append("\nFonte do alerta: ").append(alert.source);
        if (!alert.action.isEmpty()) msg.append("\nAção: ").append(alert.action);
        if (!alert.reason.isEmpty()) msg.append("\n\nMotivo: ").append(alert.reason);
        if (!alert.snippet.isEmpty()) msg.append("\n\nTrecho: ").append(alert.snippet);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(alert.level)
                .setMessage(msg.toString())
                .setPositiveButton("Fechar", null);
        if (!alert.number.isEmpty()) {
            builder.setNeutralButton("Copiar número", (d, w) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Número RJP Guard", alert.number));
                    Toast.makeText(this, "Número copiado", Toast.LENGTH_SHORT).show();
                }
            });
        }
        builder.show();
    }

    private Button actionButton(String text) {
        Button b=new Button(this); b.setText(text); b.setTextSize(15); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackgroundColor(Color.rgb(10,102,216)); b.setAllCaps(false); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)); lp.setMargins(0,dp(6),0,dp(6)); b.setLayoutParams(lp); return b;
    }
    private Button secondaryButton(String text) { Button b=actionButton(text); b.setBackgroundColor(Color.rgb(35,63,99)); return b; }
    private Button dangerButton(String text) { Button b=actionButton(text); b.setBackgroundColor(Color.rgb(179,38,30)); return b; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
