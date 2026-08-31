# RJP Guard 3.4.0

- Nova área **APPS CONFIÁVEIS**.
- Identificação da app de origem pelo `packageName` real do Android.
- Três modos por app:
  - ANALISAR NORMALMENTE;
  - CONFIÁVEL — suprime avisos amarelos fracos, mas mantém alertas fortes de possível burla;
  - IGNORAR — não analisa notificações dessa app.
- Apps são registadas localmente quando originam notificações.
- Ao ligar o acesso às notificações, o RJP Guard tenta registar também apps com notificações já ativas.
- Gestor mostra nome da app, packageName e regra atual.
- Regras ficam apenas no dispositivo; sem sincronização.
- CallShield, FTC DNC, ANACOM, base local e arquitetura multi-fonte da V3.3 mantidas.
- Histórico identificável de chamadas/mensagens mantido.
- Versão Android atualizada para `3.4.0` / versionCode `34`.
