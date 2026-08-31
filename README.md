# RJP Guard V3.6

Aplicação Android de proteção contra chamadas, números reportados e mensagens/notificações suspeitas.

## Fontes

- **ANACOM** — validação/classificação da numeração portuguesa; não é blacklist.
- **CallShield** — fonte internacional de reputação/spam.
- **FTC Do Not Call** — relatos oficiais de chamadas indesejadas/robocalls; não equivalem, isoladamente, a fraude confirmada.
- **RJP Guard Community** — base comunitária própria alojada numa Google Sheet através de Google Apps Script.
- **tellows Portugal** — arquitetura preparada, desativada enquanto não existir API/licença configurada.

## Endpoint comunitário configurado

`https://script.google.com/macros/s/AKfycbzKB1z-XRZyA3gl2Os1VCvAFpob1y92aM9SvsLjq2BfhA51aiVxgtkw9tKVLFCjDWmm4Q/exec`

A aplicação descarrega a base comunitária para SQLite e consulta-a localmente durante chamadas. Não depende de uma pesquisa online durante os segundos críticos do CallScreeningService.

## Reportes

Ao reportar/bloquear um número:

1. O número fica imediatamente bloqueado localmente.
2. É escolhida uma categoria.
3. O utilizador decide se quer partilhar o reporte de forma anónima com a RJP Guard Community.

São enviados apenas número, categoria, país, versão da app e um UUID aleatório da instalação.

## Apps confiáveis / observadas

O RJP Guard guarda o `packageName` real das apps que geram notificações. O utilizador pode escolher:

- analisar normalmente;
- confiar e suprimir apenas avisos fracos;
- ignorar completamente a app.

O cartão **Apps observadas** abre a lista das apps já escrutinadas, com data da última análise e contadores.

## Atualização do Apps Script

Antes de usar o envio comunitário, abre `server/ATUALIZAR_APPS_SCRIPT.txt` e substitui o Código.gs pela versão `server/Code.gs`, depois cria uma nova versão da implementação Web App. O URL `/exec` pode manter-se igual.

## Build

GitHub Actions: `Actions > Build RJP Guard APK`.
O workflow gera `app-debug.apk`.

> Nota: para garantir atualização Android por cima de versões anteriores sem perder dados locais, as APKs instaladas precisam de ser assinadas com a mesma chave. Builds debug de runners diferentes podem não manter a mesma assinatura; para distribuição continuada deve ser configurada uma chave de release estável nos GitHub Secrets.
