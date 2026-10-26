# RJP Guard V4.0 — Data Intelligence Update

Esta versão reforça as fontes gratuitas do RJP Guard sem depender do Sync.ME.

## Novidades V4.0
- Mantém CallShield, FTC, ANACOM e RJP Guard Community.
- Integra feeds públicos `hot_numbers.json`, `hot_ranges.json` e `spam_domains.json` da CallShield.
- Números em campanha recente podem gerar `POSSÍVEL BURLA`.
- Gamas/prefixos em campanha geram `ATENÇÃO`, sem bloqueio automático isolado.
- Domínios reportados na CallShield reforçam a análise local de SMS/WhatsApp/links.
- Atualização de tendências a cada 30 minutos quando necessário.
- Tudo é guardado localmente em SQLite antes de ser usado na filtragem.
- Mantém reconhecimento de Google Play e apps confiáveis da V3.9.

# RJP Guard V3.9 — Trusted Update Recognition

Aplicação Android de proteção contra chamadas, números reportados e mensagens/notificações suspeitas.

## Novidades V3.9

### Google Play e atualizações reconhecidas
O RJP Guard deixa de tratar as notificações oficiais de atualização como simples conteúdo ignorado. Agora tenta classificá-las explicitamente como:

- **🟢 ATUALIZAÇÃO SEGURA** — Google Play / instalador oficial Android;
- **🟢 GOOGLE PLAY / PLAY PROTECT** — manutenção/segurança oficial;
- **🟢 ATUALIZAÇÃO DE APP CONFIÁVEL** — atualização operacional de uma app marcada como confiável;
- **🟢 SOLICITAÇÃO SEGURA** — estado operacional benigno de uma app confiável.

A classificação usa sempre o **packageName real**. O nome visível da app, sozinho, nunca é suficiente para a tornar confiável.

### Apps confiáveis continuam protegidas
Os quatro modos mantêm-se:

- **OBSERVAR / ANALISAR**;
- **SISTEMA / CONFIÁVEL**;
- **CONFIÁVEL**;
- **IGNORAR**.

Uma app marcada **CONFIÁVEL** pode ter atualizações, instalação, sincronização, backup ou operação concluída reconhecidos como seguros. No entanto, notificações de apps de mensagens continuam a ser analisadas porque o conteúdo vem de terceiros.

### Proteção contra falsa confiança
A exceção de “solicitação segura” não é usada quando o texto contém sinais sensíveis como ligações externas, pedido de códigos/PIN, MB WAY, IBAN, envio de dinheiro ou acesso remoto.

### Apps observadas / escrutinadas
Passa a mostrar por app:

- notificações escrutinadas;
- alertas gerados;
- solicitações seguras reconhecidas;
- atualizações/instalações seguras;
- última classificação e último resultado.

## Funcionalidades mantidas

- filtragem de chamadas Android;
- CallShield;
- FTC Do Not Call;
- validação de numeração ANACOM;
- RJP Guard Community via Google Sheets + Apps Script;
- números seguros e bloqueados localmente;
- histórico identificável de chamadas/mensagens;
- “É seguro” para falsos positivos;
- assinatura Android permanente preparada via GitHub Secrets.

## Endpoint comunitário

`https://script.google.com/macros/s/AKfycbzKB1z-XRZyA3gl2Os1VCvAFpob1y92aM9SvsLjq2BfhA51aiVxgtkw9tKVLFCjDWmm4Q/exec`

## Build

**Actions > Build RJP Guard APK**

Sem secrets de assinatura: gera `app-debug.apk`.

Com os secrets descritos em `SIGNING_SETUP.md`: gera também `app-release.apk` assinado permanentemente.
