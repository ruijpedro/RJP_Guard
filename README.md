# RJP Guard — V3.1

Aplicação Android para proteção local contra chamadas, números e mensagens suspeitas, orientada para Portugal.

## Fontes da V3.1

### 1. ANACOM — Plano Nacional de Numeração

O RJP Guard inclui uma camada local de classificação das gamas portuguesas baseada no Plano Nacional de Numeração (PNN) da ANACOM.

É usada para identificar, entre outras, gamas de:
- móvel (91, 92, 93, 96)
- telefone fixo (2)
- telefone nómada (30)
- M2M/acesso móvel à Internet (49)
- chamada grátis (800)
- custos partilhados (808/809)
- acesso universal (707/708)
- tarifa única por chamada (760/761/762)
- tarifa majorada utilitária (71)
- número pessoal (884)

**Importante:** a ANACOM não funciona como blacklist de burlas. Esta camada classifica a numeração e ajuda a explicar o tipo de número.

### 2. CallShield

A CallShield mantém-se como fonte de reputação/spam. O feed é descarregado por HTTPS e guardado em SQLite.
Durante uma chamada, o RJP Guard consulta apenas a cópia local — não envia o número recebido para a Internet.

### 3. RJP Guard Local

O utilizador pode:
- marcar números como seguros;
- bloquear números;
- reportar localmente por categoria: Burla, Telemarketing, Falso banco, Falso familiar, Encomenda/CTT, Energia/telecomunicações, Chamada automática ou Outro.

## Ordem de decisão

1. Contacto da agenda / lista segura → **SEGURO**
2. Número bloqueado/reportado localmente → **BLOQUEADO**
3. Número encontrado na CallShield → **POSSÍVEL BURLA**
4. Gama ANACOM de tarifação sensível → **ATENÇÃO**
5. Número nacional reconhecido mas sem denúncias → **NÚMERO SEM DENÚNCIAS CONHECIDAS**
6. Restantes → **NÚMERO DESCONHECIDO**

Não são usadas percentagens.

## Novidades da V3.1

- Motor único de reputação com indicação da **fonte do alerta**.
- Camada Portugal/ANACOM.
- CallShield mantida e atualizável.
- Categorias de reporte local.
- Verificação manual de número com tipo de numeração.
- Verificação manual de mensagem/link.
- **Partilhar → RJP Guard** para analisar texto proveniente de outra aplicação.
- Deteção local reforçada de falso familiar, MB WAY, códigos, CTT, Finanças/IRS, urgência, links encurtados e pedidos de acesso remoto.
- Bloqueio automático da CallShield continua opcional; por defeito é apenas aviso.
- Histórico local com indicação da fonte.

## Privacidade

- Análise de mensagens: local.
- Verificação de chamadas: local.
- Base CallShield: descarregada periodicamente e consultada localmente.
- Lista RJP Guard: apenas no dispositivo.
- Sem conta, sem analytics e sem envio do conteúdo das mensagens para servidores.

## Limitações

- A camada ANACOM classifica gamas; não prova que um chamador seja legítimo.
- O número apresentado numa chamada pode ser falsificado por spoofing.
- A CallShield é uma base comunitária e pode ter falsos positivos/negativos.
- A V3.1 não faz scraping de sites portugueses nem inclui bases sem licença/API de reutilização clara.
- Não ouve nem transcreve o áudio de chamadas.

## Compilar no GitHub

1. Criar um repositório GitHub.
2. Colocar o conteúdo deste ZIP na raiz do repositório.
3. Abrir **Actions**.
4. Executar **Build RJP Guard APK**.
5. Descarregar o artefacto **RJP-Guard-debug-apk**.
6. O APK encontra-se como `app-debug.apk`.

## Identificação técnica

- Package: `pt.rjp.guard`
- Version name: `3.1.0`
- Version code: `31`
- Min Android: 10 / API 29
- Target: Android 15 / API 35
- Java: 17
- Gradle GitHub: 8.11.1
- Base: SQLite
