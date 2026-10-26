# RJP Guard V3.7

## Trusted System Apps

- Novo modo **SISTEMA/CONFIÁVEL** para apps e componentes cuja notificação não deve ser tratada como ameaça.
- Google Play Store (`com.android.vending`) entra automaticamente neste modo quando é observada.
- Também foram incluídos, de forma conservadora, Google Play services e componentes oficiais de permissões/instalação do Android.
- O utilizador pode sempre alterar a regra manualmente no separador **Apps Confiáveis / Observadas**.
- Novo botão **Repor automático** na gestão da regra de cada app.
- Mantém-se o modo **CONFIÁVEL**, indicado para WhatsApp/SMS: suprime avisos fracos mas continua a alertar para padrões fortes de burla.
- Mantêm-se os modos **OBSERVAR/ANALISAR** e **IGNORAR**.
- Apps em modo SISTEMA/CONFIÁVEL continuam a aparecer em **Apps Observadas / Escrutinadas**, com o resultado “SISTEMA/CONFIÁVEL”, mas as suas notificações normais não geram alertas.
- `versionCode` 38 e `versionName` 3.7.0.

## Mantido da V3.6.1

- RJP Guard Community via Google Apps Script.
- CallShield, FTC e camada ANACOM.
- Base SQLite multi-fonte.
- Histórico identificável de chamadas/mensagens.
- Gestão de Apps Observadas/Escrutinadas.
- Reporte local e comunitário opcional.
