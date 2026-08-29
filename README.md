# RJP Guard — V3.3

Aplicação Android para proteção local contra chamadas, números e mensagens suspeitas, orientada para Portugal e com base local multi-fonte.

## Novidade principal da V3.3 — base multi-fonte

A base SQLite deixou de ter apenas uma origem por número. Cada número pode agora manter registos independentes por fonte (`number + source`) e o motor junta a proveniência no alerta.

### Fontes ativas

1. **ANACOM — Plano Nacional de Numeração (Portugal)**
   - camada oficial de validação/classificação de numeração portuguesa;
   - não é blacklist e nunca é usada isoladamente para afirmar burla.

2. **CallShield**
   - continua como fonte principal de reputação anti-spam;
   - feed público descarregado por HTTPS para SQLite;
   - o projeto CallShield agrega várias fontes públicas/comunitárias e mantém proveniência própria.

3. **FTC Do Not Call — Federal Trade Commission (EUA)**
   - nova fonte oficial internacional;
   - o RJP Guard procura automaticamente o ficheiro CSV diário mais recente publicado pela FTC;
   - os números são guardados localmente com data da fonte;
   - são relatos de consumidores e **não são verificados pela FTC**, por isso um registo FTC isolado gera `ATENÇÃO / NÚMERO REPORTADO` e não `POSSÍVEL BURLA`.

### Fonte preparada, mas não ativada

4. **tellows Portugal**
   - integração identificada como futura fonte portuguesa de reputação;
   - requer API/parceria/licença válida;
   - a V3.3 não faz scraping nem reutiliza dados sem autorização.

### Fonte pessoal

5. **RJP Guard Local**
   - números seguros;
   - números bloqueados/reportados;
   - categorias locais de reporte.

## Ordem de decisão

1. Contacto da agenda / lista segura → **SEGURO**
2. Número bloqueado/reportado pelo utilizador → **BLOQUEADO**
3. CallShield → **POSSÍVEL BURLA / SPAM**
4. FTC DNC isolada → **NÚMERO REPORTADO / ATENÇÃO**
5. Gama ANACOM sensível → **ATENÇÃO**
6. Número português reconhecido sem registos → **SEM DENÚNCIAS CONHECIDAS**
7. Restantes → **NÚMERO DESCONHECIDO**

Não são usadas percentagens.

## Atualização das fontes

O botão **ATUALIZAR TODAS AS FONTES** atualiza:
- CallShield;
- FTC DNC.

A CallShield é verificada semanalmente. A FTC DNC pode ser verificada diariamente e o sincronizador recua até 10 dias para lidar com fins de semana e feriados.

Durante uma chamada, não é feita pesquisa online do número: o `CallScreeningService` consulta apenas SQLite local para não depender de rede dentro da janela de decisão do Android.

## Histórico V3.2 mantido

A V3.3 mantém o Histórico de Alertas identificável:
- número completo quando disponibilizado pelo Android;
- nome/contacto quando reconhecido;
- chamada ou app de mensagens;
- data/hora;
- motivo;
- fonte(s);
- ação tomada;
- trecho da mensagem suspeita, quando aplicável.

## Privacidade

- análise de chamadas e mensagens local;
- bases externas apenas são descarregadas periodicamente;
- sem conta;
- sem sincronização entre telemóveis;
- sem analytics;
- sem envio do conteúdo das mensagens para servidores;
- sem scraping de serviços de reputação.

## Limitações importantes

- números de chamadas podem ser falsificados por spoofing;
- um número reportado não prova que a chamada atual seja fraudulenta;
- os dados FTC são relatos não verificados;
- a ANACOM classifica numeração, não reputação;
- a CallShield pode conter falsos positivos/negativos;
- tellows não está ativa sem licença/API.

## Compilar no GitHub

1. Criar um repositório GitHub.
2. Colocar o conteúdo deste ZIP na raiz.
3. Abrir **Actions**.
4. Executar **Build RJP Guard APK**.
5. Descarregar **RJP-Guard-debug-apk**.
6. Instalar `app-debug.apk`.

## Identificação técnica

- Package: `pt.rjp.guard`
- Version name: `3.3.0`
- Version code: `33`
- Min Android: 10 / API 29
- Target: Android 15 / API 35
- Java: 17
- Gradle GitHub: 8.11.1
- Base: SQLite multi-fonte
