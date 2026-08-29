package pt.rjp.guard;

import android.content.Context;

import java.util.List;

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
            StringBuilder sources = new StringBuilder();
            String bestType = "";
            String bestDescription = "";
            for (SpamDatabase.Hit hit : hits) {
                if ("CallShield".equals(hit.source)) hasCallShield = true;
                if ("FTC DNC".equals(hit.source)) hasFtc = true;
                if (sources.indexOf(hit.source) < 0) {
                    if (sources.length() > 0) sources.append(" + ");
                    sources.append(hit.source);
                }
                if (bestType.isEmpty() && hit.type != null) bestType = hit.type;
                if (bestDescription.isEmpty() && hit.description != null && !hit.description.isEmpty()) bestDescription = hit.description;
            }

            StringBuilder detail = new StringBuilder();
            if (hasCallShield) detail.append("Número presente na base CallShield");
            if (hasFtc) {
                if (detail.length() > 0) detail.append(" e também ");
                detail.append("reportado na base oficial FTC Do Not Call");
            }
            if (!bestType.isEmpty() && !"spam".equalsIgnoreCase(bestType)) detail.append(" • ").append(bestType.replace('_', ' '));
            if (!bestDescription.isEmpty()) detail.append(". ").append(bestDescription);
            if (hasFtc) detail.append(" A FTC publica relatos de consumidores; a presença nesta fonte não prova, por si só, fraude.");

            String title = hasCallShield ? "🔴 POSSÍVEL BURLA" : "🟡 NÚMERO REPORTADO";
            Verdict verdict = hasCallShield ? Verdict.SCAM : Verdict.ATTENTION;
            return new Result(verdict, title, detail.toString(), sources.toString(), pnn);
        }

        if (pnn.level == PortugalNumbering.Level.ATTENTION) {
            return new Result(Verdict.ATTENTION, "🟡 ATENÇÃO", pnn.detail,
                    PortugalNumbering.SOURCE, pnn);
        }

        if (pnn.portuguese && pnn.level == PortugalNumbering.Level.NORMAL) {
            return new Result(Verdict.ATTENTION, "🟡 NÚMERO SEM DENÚNCIAS CONHECIDAS",
                    "Numeração portuguesa reconhecida: " + pnn.category + ". Não encontrado nas bases locais atuais.",
                    PortugalNumbering.SOURCE + " + CallShield + FTC DNC", pnn);
        }

        return new Result(Verdict.ATTENTION, "🟡 NÚMERO DESCONHECIDO",
                pnn.detail + " Não encontrado nas bases locais. Isto não garante que seja seguro.",
                pnn.portuguese ? PortugalNumbering.SOURCE + " + CallShield + FTC DNC" : "CallShield + FTC DNC", pnn);
    }
}
