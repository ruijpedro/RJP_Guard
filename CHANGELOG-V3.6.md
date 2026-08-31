# RJP Guard V3.6

## Base Comunitária RJP Guard

- Integração do endpoint Google Apps Script/Google Sheets da RJP Guard Community.
- Nova fonte local `RJP Guard Community`, combinada com CallShield, FTC DNC e validação ANACOM.
- Atualização diária automática da base comunitária e atualização manual em `ATUALIZAR TODAS AS FONTES`.
- O mesmo número pode manter várias origens em SQLite sem perder proveniência.
- Reportes comunitários isolados são tratados de forma conservadora; CallShield continua a ser um sinal forte.

## Reportar / bloquear

- Um reporte continua a ser guardado e bloqueado imediatamente no telemóvel.
- Depois da categoria, o utilizador escolhe se quer partilhar o reporte com a comunidade.
- Só são enviados: número, categoria, país, versão da app e ID anónimo da instalação.
- Não são enviados nomes, contactos, conteúdo de SMS/WhatsApp, email, IMEI ou histórico pessoal.
- O mesmo ID de instalação não pode contar duas vezes como reporte ativo do mesmo número.

## Apps observadas

- Mantém as melhorias V3.5: tocar em `Apps observadas` abre a lista de apps que já foram observadas/escrutinadas.
- Mostra packageName, última observação, última análise, total de escrutínios, alertas e último resultado.

## Servidor

A pasta `server/` contém a versão 1.1 do Google Apps Script compatível com a V3.6.
O POST comunitário não usa uma chave estática embutida na APK; uma chave desse tipo seria extraível e não constituiria um segredo real.
