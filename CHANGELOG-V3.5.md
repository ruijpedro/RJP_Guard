# RJP Guard 3.5.0

- O cartão **Apps observadas** passa a ser clicável.
- Ao abrir, mostra todas as apps vistas pelo serviço de notificações e indica claramente quais foram realmente **escrutinadas**.
- Por app são guardados localmente:
  - nome e `packageName`;
  - última notificação observada;
  - última análise efetiva;
  - número de notificações escrutinadas;
  - número de alertas gerados;
  - último resultado da análise;
  - regra atual (Analisar / Confiável / Ignorar).
- Apps em modo **IGNORAR** aparecem como observadas, mas não escrutinadas.
- O utilizador pode tocar numa app da lista para abrir o detalhe e ir diretamente para **Gerir regra**.
- Acrescentados contadores no ecrã principal: **Apps já escrutinadas** e **Notificações escrutinadas**.
- Mantidas CallShield, FTC DNC, ANACOM, base local, histórico de alertas e regras de apps confiáveis.
- Sem sincronização entre telemóveis.
- Versão Android atualizada para `3.5.0` / versionCode `35`.
