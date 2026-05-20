# Contribuindo

Obrigado por considerar contribuir com o PSBlock.

## Regras

- Não remova funções existentes sem discussão.
- Não altere permissões sensíveis sem justificar.
- Não adicione SDK de anúncios.
- Não adicione tracking.
- Não adicione coleta de dados.
- Não enfraqueça root, wipe, SMS, Device Admin, watchdog ou bloqueios.
- Não inclua arquivos gerados pelo Android Studio/Gradle.

## Build

```bat
gradlew.bat clean --no-build-cache
gradlew.bat assembleDebug --no-build-cache
```

## Não incluir

```text
app/build/
build/
.gradle/
.idea/
local.properties
intermediates/
dexBuilderDebug/
project_dex_archive/
```
