# RJP Guard — V1

Aplicação Android simples de proteção contra chamadas e mensagens suspeitas.

## O que faz

- **Filtragem de chamadas** através do papel oficial Android `Call Screening`.
- Números da agenda e números adicionados manualmente podem ser considerados **seguros**.
- Números adicionados à lista de bloqueados são **rejeitados automaticamente**.
- Chamadas de números desconhecidos são permitidas, mas geram um aviso **ATENÇÃO**.
- **Análise local das notificações** de apps de mensagens/SMS, quando o utilizador ativa o acesso a notificações.
- Estados apresentados ao utilizador:
  - 🟢 **SEGURO**
  - 🟡 **ATENÇÃO**
  - 🔴 **POSSÍVEL BURLA**
- Sem percentagens de risco.
- Histórico local dos últimos alertas.
- Teste integrado com uma mensagem típica de falso familiar / MB WAY.

## Privacidade

A V1 não utiliza servidor nem API externa. O texto de notificações é analisado no próprio aparelho e não é enviado para fora do telemóvel. O histórico guarda apenas o tipo de alerta, a app de origem e a razão detetada; não guarda o texto completo da mensagem.

## Como compilar no GitHub

1. Criar um repositório no GitHub.
2. Carregar todo o conteúdo desta pasta/ZIP para a raiz do repositório.
3. Abrir **Actions**.
4. Escolher **Build RJP Guard APK**.
5. Executar **Run workflow** (ou fazer um push para `main`).
6. No fim, descarregar o artefacto **RJP-Guard-debug-apk**.
7. Dentro do artefacto está `app-debug.apk`.

## Primeira utilização

Ao abrir a aplicação:

1. Autorizar **Contactos** e **Notificações**.
2. Tocar em **ATIVAR PROTEÇÃO DE CHAMADAS** e escolher RJP Guard como app de filtragem.
3. Tocar em **ATIVAR ANÁLISE DE MENSAGENS** e permitir acesso às notificações para RJP Guard.
4. Adicionar números seguros ou bloqueados conforme necessário.

## Limitações da V1

- Não ouve nem transcreve chamadas.
- Não analisa áudio em tempo real.
- A deteção de mensagens é baseada em regras locais e palavras/padrões típicos de burla.
- O Android pode limitar alguns dados de chamadas consoante fabricante/versão/configuração.
- O acesso às notificações permite analisar mensagens visíveis nas notificações; não transforma a app num leitor completo de WhatsApp/SMS.
- É uma ajuda preventiva; não substitui a confirmação direta com a pessoa, banco ou entidade oficial.

## Identificação técnica

- Package: `pt.rjp.guard`
- Min Android: 10 (API 29)
- Target: Android 15 (API 35)
- Java: 17
- Gradle no GitHub: 8.11.1
