# RJP Guard V3.9 — Trusted Update Recognition

## Novidades

- Reconhecimento explícito de **atualizações/instalações do Google Play** através do `packageName` real.
- Reconhecimento de notificações de atualização provenientes do **Google Play services** e dos instaladores oficiais Android/Google.
- Apps marcadas pelo utilizador como **CONFIÁVEIS** podem ter atualizações e operações benignas classificadas como seguras sem gerar falsos alertas.
- Apps de mensagens (SMS, WhatsApp, Telegram, Messenger, Gmail/Outlook) **continuam a analisar conteúdo**; uma app legítima pode transportar conteúdo fraudulento.
- Uma notificação com sinais fortes (links suspeitos, códigos/credenciais, MB WAY, IBAN, acesso remoto) não beneficia automaticamente da exceção de “operação segura”.
- Novo registo por app de:
  - solicitações reconhecidas como seguras;
  - atualizações/instalações seguras;
  - última classificação aplicada.
- O separador **Apps observadas / escrutinadas** mostra estes novos contadores.
- Centro de Proteção passa a mostrar o total de atualizações/instalações reconhecidas como seguras.

## Compatibilidade

- Mantém regras e histórico das versões anteriores através das mesmas `SharedPreferences`.
- Mantém CallShield, FTC, ANACOM, RJP Guard Community, números seguros/bloqueados e histórico identificável.
- Mantém o sistema de assinatura permanente preparado na V3.8.

## Versão Android

- `versionName 3.9.0`
- `versionCode 40`
