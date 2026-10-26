# RJP Guard V3.8 — Reliability Update

## Centro de Proteção
- Novo resumo operacional no ecrã principal.
- Estado de filtragem de chamadas.
- Estado de acesso/análise de notificações.
- Estado local da CallShield e RJP Guard Community.
- Contagem de decisões locais seguras e bloqueadas.

## Falsos positivos — “É seguro”
- Ao abrir um alerta com número identificado aparece **✓ É SEGURO**.
- O número entra na lista segura local.
- É removido da lista bloqueada e de um eventual reporte local.
- A lista segura continua a ter prioridade sobre bases públicas/comunitárias.

## Apps confiáveis
- Mantém os quatro modos da V3.7.
- Novo texto de recomendação por tipo de app.
- Apps oficiais de sistema continuam recomendadas como SISTEMA/CONFIÁVEL.
- Apps de mensagens/email são identificadas como conteúdo que deve continuar a ser analisado.

## Assinatura permanente
- Projeto preparado para assinatura release via GitHub Secrets.
- Nenhum keystore ou password é colocado no ZIP/repositório.
- O workflow continua a gerar debug APK e gera release assinado quando os secrets estiverem configurados.

## Versão
- `versionName 3.8.0`
- `versionCode 39`
