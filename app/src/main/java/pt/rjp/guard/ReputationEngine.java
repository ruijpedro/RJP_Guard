package pt.rjp.guard;

import android.content.Context;

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
        if (db.isKnownSpam(number)) {
            String type = db.getType(number);
            String desc = db.getDescription(number);
            StringBuilder detail = new StringBuilder("Número encontrado na base CallShield");
            if (type != null && !type.isEmpty() && !"spam".equalsIgnoreCase(type)) detail.append(" • ").append(type.replace('_', ' '));
            if (desc != null && !desc.isEmpty()) detail.append(". ").append(desc);
            return new Result(Verdict.SCAM, "🔴 POSSÍVEL BURLA", detail.toString(), "CallShield", pnn);
        }

        if (pnn.level == PortugalNumbering.Level.ATTENTION) {
            return new Result(Verdict.ATTENTION, "🟡 ATENÇÃO", pnn.detail,
                    PortugalNumbering.SOURCE, pnn);
        }

        if (pnn.portuguese && pnn.level == PortugalNumbering.Level.NORMAL) {
            return new Result(Verdict.ATTENTION, "🟡 NÚMERO SEM DENÚNCIAS CONHECIDAS",
                    "Numeração portuguesa reconhecida: " + pnn.category + ". Não encontrado nas bases locais atuais.",
                    PortugalNumbering.SOURCE + " + CallShield", pnn);
        }

        return new Result(Verdict.ATTENTION, "🟡 NÚMERO DESCONHECIDO",
                pnn.detail + " Não encontrado na CallShield. Isto não garante que seja seguro.",
                pnn.portuguese ? PortugalNumbering.SOURCE + " + CallShield" : "CallShield", pnn);
    }
}
