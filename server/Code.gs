/*
 * RJP GUARD COMMUNITY API — v1.1
 * Google Apps Script ligado à folha RJP_Guard_DB
 *
 * IMPORTANTE
 * - O endpoint de REPORT é público porque uma chave embutida numa APK não seria secreta.
 * - A proteção é feita por validação, deduplicação e limitação básica por instalação.
 * - Para uma distribuição pública em grande escala, migrar posteriormente para um backend
 *   com autenticação/App Check.
 *
 * Dados aceites no reporte:
 * número + categoria + ID anónimo da instalação + país + versão da app.
 * Não guardar nomes, contactos, IMEI, email ou conteúdo de mensagens.
 */

const RJP = {
  SHEET_REPORTES: "REPORTES",
  SHEET_DB: "RJP_Guard_DB",

  CATEGORIAS: [
    "Telemarketing",
    "Burla",
    "Banco falso",
    "Falso familiar",
    "Encomenda/CTT",
    "Energia/Telecomunicações",
    "Chamada automática",
    "Cobrança",
    "Spam",
    "Outro"
  ],

  MAX_REPORTES_HORA: 30
};

function setupRjpGuard() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  if (!ss) throw new Error("Não foi possível identificar a folha Google Sheets.");

  ["RJP_Guard_DB", "REPORTES", "FONTES", "CONFIG", "DASHBOARD", "INSTRUCOES"]
    .forEach(nome => {
      if (!ss.getSheetByName(nome)) throw new Error("Falta a folha: " + nome);
    });

  PropertiesService.getScriptProperties().setProperty("SPREADSHEET_ID", ss.getId());
  Logger.log("RJP GUARD COMMUNITY CONFIGURADA");
  Logger.log("SPREADSHEET_ID: " + ss.getId());
  return { ok: true, spreadsheet: ss.getName() };
}

function doGet(e) {
  try {
    const acao = String((e && e.parameter && e.parameter.acao) || "health").toLowerCase();

    if (acao === "health") {
      return resposta({ ok: true, servico: "RJP Guard Community", estado: "online", versao: "1.1" });
    }

    if (acao === "lookup") {
      const numero = normalizarNumero(e.parameter.numero || "");
      if (!numero) return resposta({ ok: false, erro: "Número inválido" });
      return resposta(consultarNumero(numero));
    }

    if (acao === "export") return resposta(exportarBase(e.parameter || {}));
    if (acao === "stats") return resposta(estatisticas());

    return resposta({ ok: false, erro: "Ação desconhecida" });
  } catch (erro) {
    return resposta({ ok: false, erro: String(erro.message || erro) });
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);

    let dados;
    try {
      dados = JSON.parse(e.postData.contents);
    } catch (err) {
      return resposta({ ok: false, erro: "JSON inválido" });
    }

    const acao = String(dados.acao || "report").toLowerCase();
    if (acao === "report") return resposta(registarReporte(dados));
    if (acao === "revoke") return resposta(revogarReporte(dados));

    return resposta({ ok: false, erro: "Ação POST desconhecida" });
  } catch (erro) {
    return resposta({ ok: false, erro: String(erro.message || erro) });
  } finally {
    try { lock.releaseLock(); } catch (e) {}
  }
}

function registarReporte(dados) {
  const numero = normalizarNumero(dados.numero || "");
  if (!numero) return { ok: false, erro: "Número inválido" };

  const categoria = String(dados.categoria || "").trim();
  if (!RJP.CATEGORIAS.includes(categoria)) return { ok: false, erro: "Categoria inválida" };

  const instalacao = limparIdInstalacao(dados.instalacao || dados.idInstalacao || "");
  if (!instalacao) return { ok: false, erro: "ID anónimo de instalação inválido" };

  if (!permitirReporte(instalacao)) {
    return { ok: false, erro: "Limite temporário de reportes atingido" };
  }

  const sheet = getSpreadsheet().getSheetByName(RJP.SHEET_REPORTES);
  const lastRow = sheet.getLastRow();

  if (lastRow >= 2) {
    const linhas = sheet.getRange(2, 1, lastRow - 1, 10).getValues();
    for (let i = 0; i < linhas.length; i++) {
      const row = linhas[i];
      if (
        String(row[1] || "") === numero &&
        String(row[4] || "") === instalacao &&
        String(row[8] || "") === "Ativo"
      ) {
        return {
          ok: true,
          duplicado: true,
          mensagem: "Este telemóvel já reportou este número.",
          resultado: consultarNumero(numero)
        };
      }
    }
  }

  const agora = new Date();
  const idReporte = Utilities.getUuid();
  const pais = String(dados.pais || "").trim().toUpperCase() || detetarPais(numero);
  const versao = String(dados.appVersao || dados.versao || "").substring(0, 30);
  const observacoes = limparObservacoes(dados.observacoes || "");

  sheet.appendRow([
    idReporte,
    numero,
    categoria,
    agora,
    instalacao,
    "RJP Guard Community",
    pais,
    versao,
    "Ativo",
    observacoes
  ]);

  atualizarBaseNumero(numero);

  return {
    ok: true,
    duplicado: false,
    idReporte: idReporte,
    numero: numero,
    categoria: categoria,
    resultado: consultarNumero(numero)
  };
}

function revogarReporte(dados) {
  const numero = normalizarNumero(dados.numero || "");
  const instalacao = limparIdInstalacao(dados.instalacao || dados.idInstalacao || "");
  if (!numero || !instalacao) return { ok: false, erro: "Dados inválidos" };

  const sheet = getSpreadsheet().getSheetByName(RJP.SHEET_REPORTES);
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return { ok: false, erro: "Reporte não encontrado" };

  const valores = sheet.getRange(2, 1, lastRow - 1, 10).getValues();
  let encontrou = false;

  for (let i = 0; i < valores.length; i++) {
    if (
      String(valores[i][1]) === numero &&
      String(valores[i][4]) === instalacao &&
      String(valores[i][8]) === "Ativo"
    ) {
      sheet.getRange(i + 2, 9).setValue("Revogado");
      encontrou = true;
    }
  }

  if (!encontrou) return { ok: false, erro: "Reporte ativo não encontrado" };

  atualizarBaseNumero(numero);
  return { ok: true, numero: numero, estado: "Revogado" };
}

function atualizarBaseNumero(numero) {
  const ss = getSpreadsheet();
  const reportesSheet = ss.getSheetByName(RJP.SHEET_REPORTES);
  const dbSheet = ss.getSheetByName(RJP.SHEET_DB);

  const last = reportesSheet.getLastRow();
  const reportes = last >= 2 ? reportesSheet.getRange(2, 1, last - 1, 10).getValues() : [];
  const ativos = reportes.filter(row => String(row[1]) === numero && String(row[8]) === "Ativo");

  const dbLast = dbSheet.getLastRow();
  let dbRow = -1;
  let estadoManual = "";
  let moderado = "";

  if (dbLast >= 2) {
    const dbValues = dbSheet.getRange(2, 1, dbLast - 1, 12).getValues();
    for (let i = 0; i < dbValues.length; i++) {
      if (String(dbValues[i][0]) === numero) {
        dbRow = i + 2;
        estadoManual = String(dbValues[i][7] || "");
        moderado = String(dbValues[i][9] || "");
        break;
      }
    }
  }

  // Estados moderados manualmente pelo proprietário da base têm prioridade.
  if (moderado.toLowerCase() === "sim" && (estadoManual === "Removido" || estadoManual === "Confirmado")) {
    return;
  }

  if (ativos.length === 0) {
    if (dbRow > 0) {
      dbSheet.getRange(dbRow, 8).setValue("Removido");
      dbSheet.getRange(dbRow, 12).setValue(new Date());
    }
    return;
  }

  const utilizadores = new Set();
  const categorias = {};
  let primeiro = null;
  let ultimo = null;
  let pais = "";

  ativos.forEach(row => {
    utilizadores.add(String(row[4]));
    const categoria = String(row[2]);
    categorias[categoria] = (categorias[categoria] || 0) + 1;

    const data = new Date(row[3]);
    if (!primeiro || data < primeiro) primeiro = data;
    if (!ultimo || data > ultimo) ultimo = data;
    if (!pais) pais = String(row[6] || "");
  });

  let categoriaPrincipal = "Outro";
  let maior = 0;
  Object.keys(categorias).forEach(cat => {
    if (categorias[cat] > maior) {
      maior = categorias[cat];
      categoriaPrincipal = cat;
    }
  });

  const nUtilizadores = utilizadores.size;
  const nReportes = ativos.length;
  const estado = nUtilizadores >= 2 ? "Reportado" : "Pendente";
  const observacao = estado === "Reportado"
    ? "Reportado por vários utilizadores independentes."
    : "Primeiro reporte comunitário.";

  const linha = [
    numero,
    pais || detetarPais(numero),
    categoriaPrincipal,
    nReportes,
    nUtilizadores,
    primeiro,
    ultimo,
    estado,
    "RJP Guard Community",
    "Não",
    observacao,
    new Date()
  ];

  if (dbRow > 0) dbSheet.getRange(dbRow, 1, 1, 12).setValues([linha]);
  else dbSheet.appendRow(linha);
}

function consultarNumero(numeroOriginal) {
  const numero = normalizarNumero(numeroOriginal);
  if (!numero) return { ok: false, erro: "Número inválido" };

  const sheet = getSpreadsheet().getSheetByName(RJP.SHEET_DB);
  const last = sheet.getLastRow();
  if (last < 2) return { ok: true, encontrado: false, numero: numero };

  const rows = sheet.getRange(2, 1, last - 1, 12).getValues();
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    if (String(row[0]) === numero) {
      return {
        ok: true,
        encontrado: true,
        numero: numero,
        pais: row[1],
        categoria: row[2],
        reportes: Number(row[3] || 0),
        utilizadores: Number(row[4] || 0),
        primeiroReporte: formatarData(row[5]),
        ultimoReporte: formatarData(row[6]),
        estado: row[7],
        fontes: row[8],
        moderado: row[9],
        observacoes: row[10],
        atualizado: formatarData(row[11])
      };
    }
  }

  return { ok: true, encontrado: false, numero: numero };
}

function exportarBase(params) {
  const sheet = getSpreadsheet().getSheetByName(RJP.SHEET_DB);
  const last = sheet.getLastRow();
  if (last < 2) return { ok: true, fonte: "RJP Guard Community", total: 0, numeros: [] };

  const rows = sheet.getRange(2, 1, last - 1, 12).getValues();
  let desde = null;
  if (params && params.desde) {
    const d = new Date(params.desde);
    if (!isNaN(d.getTime())) desde = d;
  }

  const numeros = [];
  rows.forEach(row => {
    const numero = String(row[0] || "");
    const estado = String(row[7] || "");
    if (!numero || estado === "Removido") return;

    const atualizado = new Date(row[11]);
    if (desde && !isNaN(atualizado.getTime()) && atualizado <= desde) return;

    numeros.push({
      numero: numero,
      pais: row[1],
      categoria: row[2],
      reportes: Number(row[3] || 0),
      utilizadores: Number(row[4] || 0),
      estado: estado,
      fontes: row[8],
      atualizado: formatarData(row[11])
    });
  });

  return {
    ok: true,
    fonte: "RJP Guard Community",
    atualizadoEm: new Date().toISOString(),
    total: numeros.length,
    numeros: numeros
  };
}

function estatisticas() {
  const ss = getSpreadsheet();
  const reportes = ss.getSheetByName(RJP.SHEET_REPORTES);
  const db = ss.getSheetByName(RJP.SHEET_DB);

  let totalReportes = 0;
  let numeros = 0;
  let pendentes = 0;
  let reportados = 0;
  let confirmados = 0;

  if (reportes.getLastRow() >= 2) {
    const dados = reportes.getRange(2, 1, reportes.getLastRow() - 1, 10).getValues();
    totalReportes = dados.filter(row => row[8] === "Ativo").length;
  }

  if (db.getLastRow() >= 2) {
    const dados = db.getRange(2, 1, db.getLastRow() - 1, 12).getValues();
    dados.forEach(row => {
      if (!row[0]) return;
      if (row[7] !== "Removido") numeros++;
      if (row[7] === "Pendente") pendentes++;
      if (row[7] === "Reportado") reportados++;
      if (row[7] === "Confirmado") confirmados++;
    });
  }

  return {
    ok: true,
    totalReportes: totalReportes,
    numerosUnicos: numeros,
    pendentes: pendentes,
    reportados: reportados,
    confirmados: confirmados
  };
}

function normalizarNumero(valor) {
  let n = String(valor || "").trim();
  if (!n) return null;

  n = n.replace(/[()\s\-.]/g, "");
  if (n.startsWith("00")) n = "+" + n.substring(2);

  if (n.startsWith("+")) {
    const digitos = n.substring(1).replace(/\D/g, "");
    if (digitos.length < 8 || digitos.length > 15) return null;
    return "+" + digitos;
  }

  n = n.replace(/\D/g, "");
  if (n.length === 9) return "+351" + n;
  if (n.startsWith("351") && n.length === 12) return "+" + n;
  if (n.length >= 10 && n.length <= 15) return "+" + n;
  return null;
}

function limparIdInstalacao(valor) {
  const id = String(valor || "").trim().replace(/[^a-zA-Z0-9_\-]/g, "").substring(0, 100);
  if (id.length < 12) return null;
  return id;
}

function limparObservacoes(valor) {
  return String(valor || "").replace(/[\r\n\t]/g, " ").trim().substring(0, 200);
}

function permitirReporte(instalacao) {
  const cache = CacheService.getScriptCache();
  const chave = "q_" + instalacao.replace(/[^a-zA-Z0-9]/g, "").substring(0, 40);
  let valor = Number(cache.get(chave) || 0);
  if (valor >= RJP.MAX_REPORTES_HORA) return false;
  cache.put(chave, String(valor + 1), 3600);
  return true;
}

function getSpreadsheet() {
  const id = PropertiesService.getScriptProperties().getProperty("SPREADSHEET_ID");
  if (!id) throw new Error("Executa primeiro a função setupRjpGuard().");
  return SpreadsheetApp.openById(id);
}

function detetarPais(numero) {
  if (numero.startsWith("+351")) return "PT";
  if (numero.startsWith("+1")) return "US/CA";
  if (numero.startsWith("+34")) return "ES";
  if (numero.startsWith("+33")) return "FR";
  if (numero.startsWith("+352")) return "LU";
  if (numero.startsWith("+49")) return "DE";
  if (numero.startsWith("+44")) return "UK";
  return "INT";
}

function formatarData(valor) {
  if (!valor) return null;
  const d = new Date(valor);
  if (isNaN(d.getTime())) return null;
  return d.toISOString();
}

function resposta(objeto) {
  return ContentService.createTextOutput(JSON.stringify(objeto))
    .setMimeType(ContentService.MimeType.JSON);
}
