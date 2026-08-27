# RJP Guard — V1.2

Aplicação Android simples de proteção contra chamadas e mensagens suspeitas, com base anti-spam pública atualizável.

## Novidades da V1.2

- Integração com uma **base pública de números conhecidos como spam** disponibilizada pelo projeto open-source CallShield.
- A base é descarregada por HTTPS e guardada localmente em SQLite.
- Durante uma chamada, o RJP Guard **não faz consultas à Internet**: verifica apenas a base local para manter a decisão rápida.
- Atualização automática quando a app é aberta e a base tem mais de 7 dias.
- Botão manual **ATUALIZAR BASE ANTI-SPAM**.
- Mostra o número de registos importados e a data da última atualização.
- Consulta manual de um número na base.
- Opção entre:
  - **Só avisar** quando o número consta da base pública (modo predefinido e conservador).
  - **Bloquear automaticamente** números encontrados na base pública.
- Contactos e números marcados como seguros têm prioridade e nunca são bloqueados pela base pública.
- Continua sem percentagens: 🟢 **SEGURO**, 🟡 **ATENÇÃO**, 🔴 **POSSÍVEL BURLA**.

## Fonte da base anti-spam

O RJP Guard descarrega o feed público mantido pelo projeto **CallShield**:

`https://raw.githubusercontent.com/SysAdminDoc/CallShield/master/data/spam_numbers.json`

O projeto CallShield publica o feed para sincronização local das suas aplicações e mantém informação de proveniência/licença no próprio repositório. O RJP Guard **não inclui uma cópia fixa desta base no APK**; descarrega-a diretamente da origem pública.

A maior parte das fontes atuais dessa base é internacional e tem forte cobertura de EUA/Canadá. Por isso, a cobertura de números portugueses (+351) pode ser inferior. A estrutura da V1.2 fica preparada para adicionar futuramente uma fonte portuguesa adicional.

## O que faz

- **Filtragem de chamadas** através do papel oficial Android `Call Screening`.
- Números da agenda e números adicionados manualmente podem ser considerados **seguros**.
- Números adicionados à lista de bloqueados são **rejeitados automaticamente**.
- Números encontrados na base pública geram **POSSÍVEL BURLA** e podem ser bloqueados se a opção estiver ativa.
- Chamadas de outros números desconhecidos são permitidas, mas geram um aviso **ATENÇÃO**.
- **Análise local das notificações** de apps de mensagens/SMS, quando o utilizador ativa o acesso a notificações.
- Histórico local dos últimos alertas.
- Teste integrado com uma mensagem típica de falso familiar / MB WAY.

## Privacidade

O texto das notificações é analisado no próprio aparelho e não é enviado para fora do telemóvel. A verificação de chamadas usa a base SQLite local. A única comunicação de rede criada na V1.2 é o download periódico do ficheiro público da base anti-spam.

O RJP Guard não envia o número recebido para CallShield, Truecaller, tellows ou outro serviço durante a chamada.

## Como compilar no GitHub

1. Criar um repositório no GitHub.
2. Carregar todo o conteúdo desta pasta/ZIP para a raiz do repositório.
3. Abrir **Actions**.
4. Escolher **Build RJP Guard APK**.
5. Executar **Run workflow** (ou fazer um push para `main`).
6. No fim, descarregar o artefacto **RJP-Guard-debug-apk**.
7. Dentro do artefacto está `app-debug.apk`.

## Primeira utilização

1. Autorizar **Contactos** e **Notificações**.
2. Tocar em **ATIVAR PROTEÇÃO DE CHAMADAS** e escolher RJP Guard como app de filtragem.
3. Tocar em **ATIVAR ANÁLISE DE MENSAGENS** e permitir acesso às notificações.
4. Tocar em **ATUALIZAR BASE ANTI-SPAM** na primeira instalação se a atualização automática ainda estiver a decorrer.
5. Manter inicialmente **BASE PÚBLICA: SÓ AVISAR**; depois, se quiseres, ativa o bloqueio automático.

## Limitações

- Não ouve nem transcreve chamadas.
- Não analisa áudio em tempo real.
- A deteção de mensagens é baseada em regras locais e palavras/padrões típicos de burla.
- Bases comunitárias podem conter falsos positivos.
- A cobertura portuguesa depende dos números +351 existentes nas fontes públicas.
- O Android pode limitar alguns dados de chamadas consoante fabricante/versão/configuração.
- É uma ajuda preventiva; não substitui a confirmação direta com a pessoa, banco ou entidade oficial.

## Identificação técnica

- Package: `pt.rjp.guard`
- Versão: `1.2.0`
- Version code: `3`
- Min Android: 10 (API 29)
- Target: Android 15 (API 35)
- Java: 17
- Gradle no GitHub: 8.11.1
- Base local: SQLite (`rjp_guard_spam.db`)
- Rede: HTTPS apenas para atualização do feed público

## Ícone RJP Guard

Inclui o pacote de ícones RJP Guard para mdpi, hdpi, xhdpi, xxhdpi e xxxhdpi, incluindo adaptive icon para Android 8+ e imagem 512×512 para publicação.
