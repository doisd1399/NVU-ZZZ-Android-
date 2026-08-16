# Gerar NVU R3.34 Release pelo Android usando GitHub Actions

Este pacote inclui `.github/workflows/build-android-release.yml`.

O workflow é manual e gera **APK release assinado**. A keystore oficial e as senhas não ficam no repositório e não entram no APK; são lidas dos GitHub Actions Secrets e apagadas do runner ao final.

## 1. Enviar o projeto ao GitHub

Crie um repositório privado e envie o conteúdo da pasta `NVU-CAPACITOR-ANDROID-R3.34` para a raiz do repositório. A pasta `.github/workflows` precisa permanecer no projeto.

Não envie sua keystore `.jks` como arquivo do repositório.

## 2. Preparar a keystore em Base64

Você precisa da **mesma keystore usada para assinar as versões NVU anteriores**. Criar outra chave impedirá atualização do mesmo `com.nvu.operacional`.

Uma forma prática pelo Android é usar o Google Cloud Shell: faça upload apenas temporário do `.jks` e rode:

```bash
base64 -w 0 SUA-KEYSTORE.jks > keystore-base64.txt
```

Abra `keystore-base64.txt`, copie todo o conteúdo e depois apague os dois arquivos do Cloud Shell se não precisar mais deles.

## 3. Criar os GitHub Actions Secrets

No repositório pelo navegador do Android:

`Settings → Secrets and variables → Actions → New repository secret`

Crie exatamente estes quatro Secrets:

- `ANDROID_KEYSTORE_BASE64` — conteúdo Base64 da keystore oficial.
- `ANDROID_KEYSTORE_PASSWORD` — senha da keystore.
- `ANDROID_KEY_ALIAS` — alias da chave.
- `ANDROID_KEY_PASSWORD` — senha da chave/alias.

Nunca coloque esses valores em arquivos do projeto, Issues, commits ou mensagens públicas.

## 4. Proteção extra recomendada contra chave errada

O workflow pode bloquear automaticamente uma keystore diferente da oficial.

Depois de uma primeira compilação conhecida/correta, copie o valor de `signing-cert-sha256.txt` do artefato. No GitHub vá em:

`Settings → Secrets and variables → Actions → Variables → New repository variable`

Crie:

- Nome: `EXPECTED_SIGNING_CERT_SHA256`
- Valor: SHA-256 do certificado oficial.

A partir daí, qualquer build assinado com outra chave falhará antes de entregar o APK.

## 5. Gerar o APK pelo celular

No GitHub:

`Actions → Build NVU Android Release → Run workflow → Run workflow`

O servidor executará automaticamente:

1. Node 22;
2. Java 21;
3. Android SDK 36;
4. dependências do projeto e Functions;
5. `verify:release` da R3.34;
6. build Web e sincronização Capacitor;
7. `assembleRelease`;
8. `zipalign`;
9. assinatura com a keystore oficial;
10. verificação de assinatura, `applicationId`, `versionCode` e `versionName`;
11. SHA-256 do APK.

## 6. Baixar no Android

Quando o workflow ficar verde:

`Actions → execução concluída → Artifacts → NVU-R3.34-PC-HF5-release`

O ZIP do artefato conterá:

- `NVU-R3.34-PC-HF5-release.apk`
- `NVU-R3.34-PC-HF5-release.apk.sha256`
- `signing-report.txt`
- `signing-cert-sha256.txt`
- `package-badging.txt`
- `release-info.txt`

O APK esperado deve ser:

- applicationId: `com.nvu.operacional`
- versionName: `1.0.57`
- versionCode: `57`

## Firebase Functions da R3.34

A geração do APK não publica o backend. Como a R3.34 alterou as Functions para corrigir a integridade monetária, publique-as separadamente antes de homologar novos registros automáticos:

```bash
export FUNCTIONS_DISCOVERY_TIMEOUT=60
firebase deploy --only functions --project vtc-frota-log
```

A HF5 é somente Android nativo em relação à HF4. Se o Web/Functions da HF2/HF3 já foram publicados corretamente, não republique Netlify ou Functions apenas por causa da HF5. Se você estiver vindo diretamente da R3.34 original/HF1, siga o guia HF5 completo porque as mudanças anteriores de origem/Web/Functions ainda precisam estar publicadas.
