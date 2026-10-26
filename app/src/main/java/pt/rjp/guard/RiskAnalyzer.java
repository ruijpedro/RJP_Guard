package pt.rjp.guard;

import android.content.Context;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RiskAnalyzer {
    public enum Level { SAFE("SEGURO"), WARNING("ATENÇÃO"), SCAM("POSSÍVEL BURLA"); public final String label; Level(String l){label=l;} }
    public static final class Result { public final Level level; public final String reason; Result(Level l,String r){level=l;reason=r;} }

    private static final Pattern URL = Pattern.compile("(https?://|www\\.|\\b[a-z0-9][a-z0-9.-]*\\.(com|pt|net|org|xyz|top|info|click|live|site|online|shop|icu|buzz|link)\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORTENER = Pattern.compile("\\b(bit\\.ly|tinyurl\\.com|t\\.co|cutt\\.ly|rebrand\\.ly|is\\.gd)/", Pattern.CASE_INSENSITIVE);

    private RiskAnalyzer() {}

    public static Result analyze(String original) { return analyze(null, original); }

    public static Result analyze(Context context, String original) {
        if (original == null || original.trim().isEmpty()) return new Result(Level.SAFE,"Sem conteúdo para analisar.");
        String text=normalize(original); int signals=0; List<String> reasons=new ArrayList<>();

        if (containsAny(text,"novo numero","mudei de numero","perdi o telemovel","parti o telemovel","sou o teu filho","sou a tua filha","sou teu filho","sou tua filha","ola pai","ola mae")) { signals+=3; reasons.add("imitação de familiar ou mudança inesperada de número"); }
        if (containsAny(text,"transferencia","transfere","transferir","mb way","mbway","pagamento","pagar agora","envia dinheiro","mandar dinheiro","iban","referencia multibanco")) { signals+=2; reasons.add("pedido de pagamento ou transferência"); }
        if (containsAny(text,"codigo","pin","password","palavra passe","senha","token","codigo de verificacao","codigo recebido","codigo sms","otp")) { signals+=2; reasons.add("pedido de código ou credencial"); }
        if (containsAny(text,"conta bloqueada","conta suspensa","cartao bloqueado","atividade suspeita","banco","financas","autoridade tributaria","seguranca social","ctt","alfandega","encomenda retida","portagem","via verde","irs","reembolso")) { signals+=1; reasons.add("possível imitação de entidade conhecida"); }
        if (containsAny(text,"urgente","imediatamente","agora","ultimo aviso","prazo termina","evite bloqueio","evitar bloqueio","nao desligue","regularize hoje","penhora","divida pendente")) { signals+=1; reasons.add("pressão ou urgência anormal"); }
        if (URL.matcher(text).find()) { signals+=1; reasons.add("contém ligação externa"); }
        if (SHORTENER.matcher(text).find()) { signals+=2; reasons.add("usa ligação encurtada que oculta o destino"); }
        if (containsAny(text,"premio","ganhou","sorteio","oferta exclusiva","investimento garantido","lucro garantido","bitcoin","criptomoeda","crypto")) { signals+=2; reasons.add("promessa financeira ou prémio inesperado"); }
        if (containsAny(text,"instale esta app","instalar aplicacao","anydesk","teamviewer","acesso remoto","partilhar ecra")) { signals+=3; reasons.add("pedido de instalação ou acesso remoto ao dispositivo"); }
        if (context != null) {
            String badDomain = SpamDatabase.get(context).findSpamDomain(original);
            if (!badDomain.isEmpty()) { signals += 4; reasons.add("domínio reportado pela CallShield: " + badDomain); }
        }

        Level level=signals>=4?Level.SCAM:signals>=2?Level.WARNING:Level.SAFE;
        String reason=reasons.isEmpty()?"Não foram encontrados padrões simples de burla.":"Detetado: "+joinReasons(reasons)+".";
        return new Result(level,reason);
    }

    private static String normalize(String s) { return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim(); }
    private static boolean containsAny(String text,String... needles){ for(String n:needles) if(text.contains(n)) return true; return false; }
    private static String joinReasons(List<String> r){ if(r.size()==1)return r.get(0); StringBuilder sb=new StringBuilder(); for(int i=0;i<r.size();i++){if(i>0)sb.append(i==r.size()-1?" e ":", ");sb.append(r.get(i));}return sb.toString(); }
}
