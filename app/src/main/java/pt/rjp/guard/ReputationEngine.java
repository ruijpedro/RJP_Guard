package pt.rjp.guard;

import android.content.Context;

import java.util.List;
import java.util.Locale;

public final class ReputationEngine {
    public enum Verdict { SAFE, ATTENTION, SCAM, BLOCKED }

    public static final class Result {
        public final Verdict verdict;
        public final String title;
        public final String detail;
        public final String source;
        public final PortugalNumbering.Info numbering;

        Result(Verdict verdict, String title, String detail, String source, PortugalNumbering.Info numbering) {
            this.verdict = verdict;
            this.title = title;
            this.detail = detail;
            this.source = source;
            this.numbering = numbering;
        }
    }

    private ReputationEngine() {}

    public static Result lookup(Context c, String number, boolean isContact) {
        PortugalNumbering.Info pnn = PortugalNumbering.classify(number);

        if (GuardStore.isSafe(c, number) || isContact) {
            return new Result(Verdict.SAFE, "🟢 SEGURO",
                    "Número reconhecido como contacto/lista segura.", "RJP Guard — lista segura", pnn);
        }

        String localType = GuardStore.localReportType(c, number);
        if (GuardStore.isBlocked(c, number)) {
            String extra = localType.isEmpty() ? "" : " Categoria: " + localType + ".";
            return new Result(Verdict.BLOCKED, "🔴 BLOQUEADO",
                    "Número bloqueado pelo utilizador." + extra, "RJP Guard Local", pnn);
        }

        SpamDatabase db = SpamDatabase.get(c);
        List<SpamDatabase.Hit> hits = db.hits(number);
        if (!hits.isEmpty()) {
            boolean hasCallShield = false;
            boolean hasFtc = false;
            boolean hasCommunity = false;
            boolean communityConfirmed = false;
            boolean communityReported = false;
            int communityReports = 0;
            String communityType = "";
            StringBuilder sources = new StringBuilder();
            String bestType = "";
            String bestDescription = "";

            for (SpamDatabase.Hit hit : hits) {
                if ("CallShield".equals(hit.source)) hasCallShield = true;
                if ("FTC DNC".equals(hit.source)) hasFtc = true;
                if (CommunitySyncManager.SOURCE_NAME.equals(hit.source)) {
                    hasCommunity = true;
                    communityReports = Math.max(communityReports, hit.reports);
                    communityType = hit.type == null ? "" : hit.type;
                    String d = hit.description == null ? "" : hit.description.toLowerCase(Locale.ROOT);
                    if (d.contains("confirmado")) communityConfirmed = true;
                    if (d.contains("reportado") || hit.reports >= 2) communityReported = true;
                }
                if (sources.indexOf(hit.source) < 0) {
                    if (sources.length() > 0) sources.append(" + ");
                    sources.append(hit.source);
                }
                if (bestType.isEmpty() && hit.type != null) bestType = hit.type;
                if (bestDescription.isEmpty() && hit.description != null && !hit.description.isEmpty()) bestDescription = hit.description;
            }

            Verdict verdict;
            String title;
            StringBuilder detail = new StringBuilder();

            if (hasCallShield) {
                verdict = Verdict.SCAM;
                title = "🔴 POSSÍVEL BURLA";
                detail.append("Número presente na base CallShield");
            } else if (communityConfirmed) {
                verdict = Verdict.SCAM;
                title = "🔴 REPORTADO POR VÁRIAS FONTES";
                detail.append("Número confirmado na RJP Guard Community");
            } else if (hasCommunity) {
                verdict = Verdict.ATTENTION;
                title = communityReported ? "🟡 REPORTADO PELA COMUNIDADE" : "🟡 REPORTE COMUNITÁRIO";
                detail.append("Número presente na RJP Guard Community");
                if (!communityType.isEmpty()) detail.append(" • ").append(communityType);
                if (communityReports > 0) detail.append(" • ").append(communityReports).append(" reporte(s)");
            } else {
                verdict = Verdict.ATTENTION;
                title = "🟡 NÚMERO REPORTADO";
            }

            if (hasFtc) {
                if (detail.length() > 0) detail.append(". ");
                detail.append("Também consta em relatos da FTC Do Not Call");
            }
            if (detail.length() == 0 && !bestType.isEmpty()) detail.append(bestType.replace('_', ' '));
            if (!bestDescription.isEmpty() && !detail.toString().contains(bestDescription)) detail.append(". ").append(bestDescription);
            if (hasFtc) detail.append(". A FTC publica relatos de consumidores; a presença nesta fonte não prova, por si só, fraude.");

            return new Result(verdict, title, detail.toString(), sources.toString(), pnn);
        }

        if (pnn.level == PortugalNumbering.Level.ATTENTION) {
            return new Result(Verdict.ATTENTION, "🟡 ATENÇÃO", pnn.detail,
                    PortugalNumbering.SOURCE, pnn);
        }

        String checkedSources = PortugalNumbering.SOURCE + " + CallShield + FTC DNC + RJP Guard Community";
        if (pnn.portuguese && pnn.level == PortugalNumbering.Level.NORMAL) {
            return new Result(Verdict.ATTENTION, "🟡 NÚMERO SEM DENÚNCIAS CONHECIDAS",
                    "Numeração portuguesa reconhecida: " + pnn.category + ". Não encontrado nas bases locais atuais.",
                    checkedSources, pnn);
        }

        return new Result(Verdict.ATTENTION, "🟡 NÚMERO DESCONHECIDO",
                pnn.detail + " Não encontrado nas bases locais. Isto não garante que seja seguro.",
                pnn.portuguese ? checkedSources : "CallShield + FTC DNC + RJP Guard Community", pnn);
    }
}
