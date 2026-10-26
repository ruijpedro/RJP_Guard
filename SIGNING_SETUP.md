# RJP Guard — assinatura permanente

A assinatura permanente permite instalar V3.9, V4, etc. por cima da mesma app sem perder dados locais.

## Importante
- Não publiques o ficheiro `.jks` no repositório.
- Não coloques passwords no código.
- Guarda uma cópia segura do keystore. Se o perderes, futuras APKs não poderão atualizar a app instalada com essa assinatura.
- Se as versões anteriores foram instaladas com a chave debug temporária do GitHub Actions, a primeira passagem para a assinatura permanente pode exigir desinstalar a versão anterior. Depois disso, usa sempre o APK `release-signed`.

## 1. Criar a chave uma única vez

Num computador com Java instalado:

```bash
keytool -genkeypair -v -keystore rjp-guard-release.jks -alias rjpguard -keyalg RSA -keysize 4096 -validity 10000
```

Escolhe e guarda as passwords.

## 2. Converter para Base64

### Windows PowerShell

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("rjp-guard-release.jks")) | Set-Content rjp-guard-release-base64.txt
```

### Linux/macOS

```bash
base64 -w 0 rjp-guard-release.jks > rjp-guard-release-base64.txt
```

## 3. Criar GitHub Secrets

No repositório: **Settings > Secrets and variables > Actions > New repository secret**.

Cria:

- `RJP_KEYSTORE_BASE64` — conteúdo do ficheiro Base64
- `RJP_KEYSTORE_PASSWORD` — password do keystore
- `RJP_KEY_ALIAS` — normalmente `rjpguard`
- `RJP_KEY_PASSWORD` — password da chave

## 4. Compilar

Executa **Actions > Build RJP Guard APK**.

O workflow continuará a gerar o APK debug e, quando os 4 secrets existirem, também:

`RJP-Guard-release-signed-apk/app-release.apk`

Instala sempre o APK release assinado nas versões seguintes.
