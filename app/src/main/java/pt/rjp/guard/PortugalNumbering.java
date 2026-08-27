package pt.rjp.guard;

/**
 * Classificação local do Plano Nacional de Numeração (Portugal).
 * Regras baseadas nas gamas publicadas pela ANACOM. Não é uma blacklist.
 */
public final class PortugalNumbering {
    public static final String SOURCE = "ANACOM — Plano Nacional de Numeração";
    public static final String SOURCE_UPDATED = "29/07/2026";
    public static final String SOURCE_URL = "https://www.anacom.pt/render.jsp?categoryId=423086";

    public enum Level { NORMAL, ATTENTION, UNKNOWN, FOREIGN }

    public static final class Info {
        public final boolean portuguese;
        public final Level level;
        public final String category;
        public final String detail;

        Info(boolean portuguese, Level level, String category, String detail) {
            this.portuguese = portuguese;
            this.level = level;
            this.category = category;
            this.detail = detail;
        }
    }

    private PortugalNumbering() {}

    public static Info classify(String raw) {
        String n = SpamDatabase.canonical(raw);
        if (n.isEmpty()) return new Info(false, Level.UNKNOWN, "Número vazio", "Não foi possível classificar.");

        boolean explicitPt = n.startsWith("351");
        if (explicitPt) n = n.substring(3);

        // Números curtos nacionais (112, 116xxx, etc.) não são tratados como chamadas comuns.
        if (n.length() <= 6 && !explicitPt) {
            return new Info(true, Level.NORMAL, "Número curto / serviço", "Numeração curta nacional ou de serviço.");
        }

        // Se vier com indicativo internacional diferente de +351, tratamos como estrangeiro.
        if (raw != null && raw.trim().startsWith("+") && !raw.replaceAll("\\s", "").startsWith("+351")) {
            return new Info(false, Level.FOREIGN, "Número internacional", "Fora do Plano Nacional de Numeração português.");
        }

        if (n.length() != 9) {
            return new Info(explicitPt, Level.UNKNOWN, "Formato não reconhecido", "O formato não corresponde às gamas nacionais comuns classificadas localmente.");
        }

        if (startsAny(n, "91", "92", "93", "96"))
            return normal("Serviço telefónico móvel", "Gama móvel nacional: 91/92/93/96.");
        if (n.startsWith("2"))
            return normal("Serviço telefónico em local fixo", "Gama geográfica/fixa nacional iniciada por 2.");
        if (n.startsWith("30"))
            return normal("Serviço telefónico nómada", "Gama nómada nacional iniciada por 30.");
        if (n.startsWith("49"))
            return normal("M2M / acesso móvel à Internet", "Gama nacional iniciada por 49.");
        if (n.startsWith("800"))
            return normal("Chamada grátis para o chamador", "Gama 800.");
        if (startsAny(n, "808", "809"))
            return attention("Custos partilhados", "Gama 808/809; confirme os custos antes de ligar de volta.");
        if (startsAny(n, "707", "708"))
            return attention("Acesso universal", "Gama 707/708; pode ter tarifação própria.");
        if (startsAny(n, "760", "761", "762"))
            return attention("Tarifa única por chamada", "Gama 760/761/762; chamada potencialmente onerosa.");
        if (n.startsWith("71"))
            return attention("Serviço utilitário de tarifa majorada", "Gama 71; atenção à tarifação.");
        if (n.startsWith("884"))
            return normal("Número pessoal", "Gama 884.");

        return new Info(true, Level.UNKNOWN, "Outra gama nacional", "Não corresponde às gamas principais classificadas localmente. Confirmar na pesquisa oficial ANACOM.");
    }

    private static Info normal(String category, String detail) {
        return new Info(true, Level.NORMAL, category, detail);
    }

    private static Info attention(String category, String detail) {
        return new Info(true, Level.ATTENTION, category, detail);
    }

    private static boolean startsAny(String n, String... prefixes) {
        for (String p : prefixes) if (n.startsWith(p)) return true;
        return false;
    }
}
