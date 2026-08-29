# Fontes e dados de terceiros — RJP Guard 3.3

## ANACOM — Plano Nacional de Numeração
Página oficial: https://www.anacom.pt/render.jsp?categoryId=423086

Uso: validação e classificação local das gamas de numeração portuguesas. A ANACOM não fornece uma blacklist de burlas nesta integração.

## CallShield
Projeto: https://github.com/SysAdminDoc/CallShield
Feed legado estável consumido pela app: `data/spam_numbers.json` no repositório upstream.

Uso: reputação anti-spam local. O RJP Guard não apresenta os dados upstream como dados oficiais portugueses. Consulte o manifesto/licenças do projeto CallShield para proveniência e condições das fontes agregadas.

## Federal Trade Commission — Do Not Call Reported Calls Data
Página oficial: https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data

Uso: CSV diário público de chamadas indesejadas/robocalls reportadas por consumidores. Os próprios dados FTC indicam que os relatos não são verificados. A V3.3 trata um registo FTC isolado como sinal de atenção, não como prova de fraude.

## tellows Portugal
API/parcerias: https://www.tellows.com/s/about-en/tellows-api-partnership-program

Estado: integração não ativa. Requer API/licença/parceria válida. O RJP Guard não faz scraping.
