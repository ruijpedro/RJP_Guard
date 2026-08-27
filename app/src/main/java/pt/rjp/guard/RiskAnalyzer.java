package pt.rjp.guard;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RiskAnalyzer {

    public enum Level {
        SAFE("SEGURO"),
        WARNING("ATENÇÃO"),
        SCAM("POSSÍVEL BURLA");

        public final String label;
        Level(String label) { this.label = label; }
    }

    public static final class Result {
        public final Level level;
        public final String reason;

        Result(Level level, String reason) {
            this.level = level;
            this.reason = reason;
        }
    }

    private static final Pattern URL = Pattern.compile("(https?://|www\\.|\\b[a-z0-9.-]+\\.(com|pt|net|org|xyz|top|info)\\b)", Pattern.CASE_INSENSITIVE);

    private RiskAnalyzer() {}

    public static Result analyze(String original) {
        if (original == null || original.trim().isEmpty()) {
            return new Result(Level.SAFE, "Sem conteúdo para analisar.");
        }

        String text = normalize(original);
        int signals = 0; // internal only; never shown to the user
        List<String> reasons = new ArrayList<>();

        if (containsAny(text,
                "novo numero", "mudei de numero", "perdi o telemovel", "parti o telemovel",
                "sou o teu filho", "sou a tua filha", "sou teu filho", "sou tua filha",
                "ola pai", "ola mae", "mae este e o meu novo numero", "pai este e o meu novo numero")) {
            signals += 3;
            reasons.add("imitação de familiar ou mudança inesperada de número");
        }

        if (containsAny(text,
                "transferencia", "transfere", "transferir", "mb way", "mbway", "pagamento",
                "pagar agora", "envia dinheiro", "mandar dinheiro", "iban", "referencia multibanco")) {
            signals += 2;
            reasons.add("pedido de pagamento ou transferência");
        }

        if (containsAny(text,
                "codigo", "pin", "password", "palavra passe", "senha", "token",
                "codigo de verificacao", "codigo recebido", "codigo sms")) {
            signals += 2;
            reasons.add("pedido de código ou credencial");
        }

        if (containsAny(text,
                "conta bloqueada", "conta suspensa", "cartao bloqueado", "atividade suspeita",
                "banco", "financas", "autoridade tributaria", "seguranca social", "ctt",
                "alfandega", "encomenda retida", "portagem", "via verde")) {
            signals += 1;
            reasons.add("possível imitação de entidade conhecida");
        }

        if (containsAny(text,
                "urgente", "imediatamente", "agora", "ultimo aviso", "prazo termina",
                "evite bloqueio", "evitar bloqueio", "nao desligue")) {
            signals += 1;
            reasons.add("pressão ou urgência anormal");
        }

        if (URL.matcher(text).find()) {
            signals += 1;
            reasons.add("contém ligação externa");
        }

        if (containsAny(text,
                "premio", "ganhou", "sorteio", "oferta exclusiva", "investimento garantido",
                "lucro garantido", "bitcoin", "criptomoeda", "crypto")) {
            signals += 2;
            reasons.add("promessa financeira ou prémio inesperado");
        }

        Level level;
        if (signals >= 4) {
            level = Level.SCAM;
        } else if (signals >= 2) {
            level = Level.WARNING;
        } else {
            level = Level.SAFE;
        }

        String reason;
        if (reasons.isEmpty()) {
            reason = "Não foram encontrados padrões simples de burla.";
        } else {
            reason = "Detetado: " + joinReasons(reasons) + ".";
        }
        return new Result(level, reason);
    }

    private static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return n.replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private static String joinReasons(List<String> reasons) {
        if (reasons.size() == 1) return reasons.get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) sb.append(i == reasons.size() - 1 ? " e " : ", ");
            sb.append(reasons.get(i));
        }
        return sb.toString();
    }
}
