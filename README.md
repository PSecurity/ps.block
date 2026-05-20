# PSBlock

**PSBlock** é um app Android de proteção local para dispositivos próprios ou ambientes autorizados, desenvolvido pela **PeekSecurity**.

O projeto foi criado para estudos, privacidade e segurança pessoal, com foco em controle local de câmera, microfone, localização, Bluetooth, políticas de proteção e ações autorizadas.

> Use somente em dispositivos próprios, ambientes de estudo ou cenários com autorização explícita.

## Recursos principais

- Dashboard de proteção local.
- Bloqueio de câmera.
- Bloqueio de microfone.
- Bloqueio de localização/GPS.
- Controle de Bluetooth.
- Botão **All** aplicando câmera, microfone, localização e Bluetooth.
- Device Admin para recursos autorizados.
- Watchdog/reaplicação de proteção.
- App lock e autenticação local.
- Logs locais de segurança.
- Políticas por rede, zona segura, agenda e chamadas.
- Support Center ético com Pix, PayPal e GitHub Sponsors.

## Distribuição

O PSBlock é distribuído pela comunidade via GitHub, fora da Play Store.

O app não usa SDK de anúncios, não vende dados e não possui rastreamento publicitário.

## Apoio ao projeto

O apoio é opcional e transparente.

- Pix: chave aleatória.
- PayPal: link externo.
- GitHub Sponsors: https://github.com/sponsors/PSecurity

Chave Pix:

```text
5a8f7456-b245-4b9c-a3ab-64b9d5891434
```

## Permissões sensíveis

O PSBlock usa permissões sensíveis porque suas funções dependem de recursos reais do Android:

- Câmera e microfone: proteção e fallback local.
- Localização: políticas de zona segura e GPS/localização.
- SMS: comandos autorizados de pânico/wipe remoto.
- Device Admin: bloqueio por política, lock e wipe autorizado.
- Acessibilidade/uso de apps: detecção local de contexto e apps monitorados.
- Bluetooth: controle local do rádio Bluetooth.

Leia também:

- [PRIVACY.md](PRIVACY.md)
- [SECURITY.md](SECURITY.md)
- [DISCLAIMER.md](DISCLAIMER.md)
- [SUPPORT.md](SUPPORT.md)

## Build

Abra no Android Studio ou use:

```bat
gradlew.bat clean --no-build-cache
gradlew.bat assembleDebug --no-build-cache
```

## Regra de empacotamento

Nunca incluir arquivos gerados no repositório ou em releases de código-fonte:

```text
app/build/
build/
.gradle/
.idea/
local.properties
intermediates/
dexBuilderDebug/
project_dex_archive/
tmp/kotlin-classes/
generated/
outputs/
```

Use sempre pacotes **source-only**.

## Licença

Apache License 2.0. Consulte [LICENSE](LICENSE) e [NOTICE](NOTICE).
