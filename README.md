<div align="center">

# 🛡️ PS.Block

### by **Gilberto Martins** · `PeekSecurity`

**Android Security Control • Camera Block • Mic Block • GPS Block • Bluetooth Block • Remote Actions**

> Aplicativo Android autoral da PeekSecurity focado em controle defensivo, proteção local e resposta rápida para recursos sensíveis do dispositivo.

<br>

[![GitHub](https://img.shields.io/badge/GITHUB-PSecurity%2Fps.block-111827?style=for-the-badge&logo=github&logoColor=white)](https://github.com/PSecurity/ps.block)
[![Portfolio](https://img.shields.io/badge/PORTFÓLIO-PeekSecurity-7c3aed?style=for-the-badge&logo=githubpages&logoColor=white)](https://psecurity.github.io/PSecurity/)
[![Android](https://img.shields.io/badge/ANDROID-Security_App-14532d?style=for-the-badge&logo=android&logoColor=white)](#)
[![Open Source](https://img.shields.io/badge/OPEN_SOURCE-Community_Project-1f2937?style=for-the-badge&logo=opensourceinitiative&logoColor=white)](#)

<br>

`Defensive control.`  
`Local protection.`  
`Built for the PeekSecurity community.`

<br>

**Camera • Microfone • Localização • Bluetooth • Device Admin • Watchdog • Ações remotas**

</div>

### O objetivo do projeto é oferecer um painel local de privacidade e segurança para controlar sensores, reforçar políticas do Android e aplicar camadas de proteção contra uso indevido de câmera, microfone, localização e Bluetooth.

> Uso responsável: utilize o PSBlock somente em dispositivo próprio, laboratório, ambiente de estudo ou cenário com autorização explícita.

---

## Índice

- [Visão geral](#visão-geral)
- [Principais recursos](#principais-recursos)
- [Como o PSBlock funciona](#como-o-psblock-funciona)
- [Dashboard](#dashboard)
- [Configurações / Control Center](#configurações--control-center)
- [Support Center](#support-center)
- [Widget](#widget)
- [Idiomas](#idiomas)
- [Privacidade e monetização ética](#privacidade-e-monetização-ética)
- [Permissões usadas](#permissões-usadas)
- [Instalação](#instalação)
- [Primeira configuração recomendada](#primeira-configuração-recomendada)
- [Build pelo Android Studio](#build-pelo-android-studio)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Arquivos que não devem ser enviados ao GitHub](#arquivos-que-não-devem-ser-enviados-ao-github)
- [Limitações importantes](#limitações-importantes)
- [Solução de problemas](#solução-de-problemas)
- [Contribuição](#contribuição)
- [Apoiar a PeekSecurity](#apoiar-a-peeksecurity)
- [Licença](#licença)
- [Aviso de responsabilidade](#aviso-de-responsabilidade)

---

## Visão geral

O PSBlock centraliza controles locais de proteção para Android em uma interface direta, com foco em:

- privacidade de sensores;
- proteção local do dispositivo;
- bloqueio e reaplicação automática de políticas;
- modo root avançado quando disponível;
- fallback funcional quando root não está disponível;
- logs locais;
- automações por contexto;
- apoio ético ao projeto, sem anúncios e sem rastreamento publicitário.

O app foi pensado para distribuição comunitária via GitHub, fora da Play Store.

---

## Principais recursos

### Proteção de sensores

O PSBlock controla os principais sensores e rádios do dispositivo:

| Recurso | Função |
|---|---|
| Câmera | Aplica bloqueio por política, root ou fallback local |
| Microfone | Aplica mute, ocupação/fallback e bloqueio root quando disponível |
| Localização/GPS | Aplica bloqueio local/root e políticas automáticas |
| Bluetooth | Permite bloquear/liberar o rádio Bluetooth |
| All | Aplica câmera + microfone + localização + Bluetooth juntos |

### Modos de proteção

O app trabalha com duas camadas principais:

| Modo | Descrição |
|---|---|
| Root | Usa recursos avançados quando o dispositivo possui root funcional |
| Fallback | Usa Device Admin, ocupação local, mute e watchdog quando root não está disponível |

### Recursos de segurança local

- Device Admin para bloqueio por política, lock e wipe autorizado.
- Watchdog para reaplicar proteção.
- Serviço foreground para manter proteção ativa.
- Logs locais de eventos.
- App lock com senha local.
- Biometria opcional.
- Integridade local do app.
- Bloqueio rápido via dashboard.
- Modo pânico.
- Wipe remoto autorizado por SMS, desligado por padrão.

### Políticas automáticas

- Bloqueio ao sair de rede Wi-Fi segura.
- Bloqueio fora de zona segura.
- Bloqueio por janela de horário.
- Bloqueio durante chamadas.
- Liberação temporária para uso autorizado.
- Apps confiáveis para liberação controlada.

---

## Como o PSBlock funciona

O PSBlock não é apenas uma tela de switches. Ele mantém uma camada de serviço que aplica e reaplica políticas de proteção no dispositivo.

### Motor principal

O serviço principal é responsável por:

- aplicar estados de câmera, microfone, localização e Bluetooth;
- reagir a mudanças feitas na dashboard;
- reaplicar proteção via watchdog;
- manter notificação foreground;
- atualizar a interface;
- registrar eventos no log local;
- respeitar liberação temporária autorizada.

### Aplicação em background

A aplicação das políticas pesadas roda fora da UI, para manter a dashboard mais fluida. Isso evita travamentos quando o app precisa aplicar comandos root, fallback de câmera/microfone ou atualizações de sensores.

### Root quando disponível

Em dispositivos com root, o PSBlock pode aplicar bloqueios mais fortes, como:

- restrições em nós de câmera;
- controle de permissões/appops;
- bloqueios relacionados a áudio/microfone;
- ações relacionadas a localização;
- controle de Bluetooth;
- controle de Wi-Fi;
- randomização de MAC;
- desativação de ADB via comandos locais.

> Root é opcional, mas alguns recursos avançados dependem dele.

### Fallback sem root

Quando root não está disponível, o PSBlock usa alternativas locais, como:

- Device Admin para política de câmera e lock;
- tentativa de ocupação de câmera;
- mute/ocupação de microfone;
- serviço persistente;
- watchdog;
- overlay de alerta;
- monitoramento local de contexto.

> O fallback é útil, mas pode não ter a mesma força de um bloqueio root em todos os dispositivos.

---

## Dashboard

A dashboard é a tela principal de operação do PSBlock.

### Controles principais

A dashboard inclui:

- **Câmera**
- **Microfone**
- **Localização**
- **Bluetooth**
- **All / Bloquear tudo**

O botão **All** aplica todos os controles principais juntos:

```text
Câmera + Microfone + Localização + Bluetooth
```

### Estado operacional

A tela mostra o estado atual do app:

- modo root ou fallback;
- sensores ativos na proteção;
- status de permissões;
- Device Admin;
- runtime permissions;
- overlay;
- bateria;
- acessibilidade;
- usage access opcional.

### Configurar permissões

O botão de configuração orienta o usuário no setup inicial, incluindo:

- permissões runtime;
- Device Admin;
- otimização de bateria;
- overlay;
- acessibilidade;
- uso de apps, quando necessário.

### Ações locais

A dashboard também oferece:

- travar dispositivo;
- abrir logs;
- abrir configurações;
- acionar modo pânico;
- acessar apoio ao projeto.

### Modo pânico

O modo pânico aplica proteção em câmera, microfone e localização, tenta travar o dispositivo e registra o evento localmente.

Use apenas no seu próprio aparelho ou em cenário autorizado.

---

## Configurações / Control Center

A tela de configurações é o centro avançado do PSBlock.

### Interface

Permite alterar o idioma do app:

- English
- Português (Brasil)

O idioma padrão do app é inglês, com opção para PT-BR.

### Acesso e sensores

Inclui recursos de proteção local:

- senha local;
- bloqueio do app;
- biometria;
- guia Sensor Off;
- desativação de ADB quando suportado.

### Senha local

A senha local protege o console do app.

Características:

- fica salva localmente como hash;
- não é enviada para servidor;
- não é usada para tracking;
- pode ser combinada com biometria.

### Biometria

A biometria pode ser usada como camada de autenticação quando:

- o dispositivo suporta biometria;
- o app lock está ativo;
- uma senha local foi definida.

### Sensor Off Guide

O app orienta sobre os toggles nativos do Android 12+ para câmera e microfone.

Esses toggles do sistema são uma camada manual forte. O PSBlock atua como camada adicional com Device Admin, serviço persistente, watchdog, root/fallback e políticas locais.

### Desativar ADB

Quando root está disponível, o PSBlock pode tentar desativar ADB como medida de endurecimento local.

---

## Rede e zona segura

O PSBlock pode aplicar políticas automáticas relacionadas a rede e localização.

### Bloquear rede desconhecida

Quando ativado, o app compara a rede Wi-Fi atual com a lista de redes seguras.

Se a rede for desconhecida, o PSBlock pode ativar bloqueio automático.

### Redes salvas

Permite salvar redes Wi-Fi consideradas confiáveis.

### Randomizar MAC

Quando root está disponível, o app pode tentar randomizar o endereço MAC como camada extra de privacidade.

### Zona segura

Permite configurar zonas onde o dispositivo é considerado em ambiente autorizado.

Se o aparelho sair da zona segura, o PSBlock pode ativar bloqueio automático.

---

## Políticas automáticas

### Liberação temporária

Permite liberar câmera e microfone por tempo limitado em um contexto autorizado.

Exemplo:

```text
Usar câmera agora
```

Essa liberação é temporária e registrada nos logs.

### Bloqueio por horário

Permite definir uma janela de horário para ativação automática de bloqueios.

Exemplos de uso:

- horário de trabalho;
- estudos;
- deslocamento;
- ambientes sensíveis;
- horários em que sensores não devem ficar disponíveis.

### Bloqueio durante chamadas

Pode ativar políticas enquanto chamadas estão em andamento, dependendo das permissões configuradas.

### Apps confiáveis

Permite configurar apps confiáveis para liberação controlada.

Quando o monitor detecta um app confiável em primeiro plano, o PSBlock pode aplicar liberação temporária autorizada.

---

## Pânico e wipe

### Pânico local

O modo pânico é acionado manualmente pelo usuário e reforça bloqueios críticos.

### SMS de pânico

Permite configurar número autorizado para ações de pânico via SMS.

### Wipe remoto por SMS

O wipe remoto é um recurso sensível e fica desligado por padrão.

Quando ativado pelo usuário, depende de:

- permissão SMS;
- código configurado;
- número autorizado;
- Device Admin ativo;
- comando correto.

> Use wipe remoto somente se entender o risco. Wipe pode apagar dados do dispositivo.

---

## Root e serviços

### Modo root

O modo root permite usar camadas avançadas de proteção local.

### Testar root

O app inclui opção para validar se root está disponível.

### Termux

O app pode abrir Termux como ferramenta auxiliar para usuários avançados.

### Reaplicar proteção

Permite solicitar reaplicação manual da proteção.

### Integridade

Executa checagens locais de integridade e estado operacional.

### Exportar e limpar logs

Permite gerenciar logs locais de segurança.

---

## Serviços internos

O PSBlock possui serviços internos para manter as funções ativas.

### PSBlockService

Serviço principal de proteção.

Responsável por:

- aplicar bloqueio de câmera;
- aplicar bloqueio de microfone;
- aplicar bloqueio de localização;
- reaplicar políticas;
- manter notificação foreground;
- executar watchdog;
- lidar com modo pânico;
- lidar com liberação temporária.

### NetworkWatchService

Serviço de monitoramento contextual.

Responsável por:

- monitorar rede Wi-Fi;
- detectar rede desconhecida;
- monitorar zona segura;
- monitorar janela de horário;
- acionar proteção automática;
- registrar eventos de rede/GPS/agenda.

### AppMonitorService

Serviço de acessibilidade para detecção local de apps em primeiro plano.

Pode ajudar a:

- detectar apps que usam sensores;
- identificar apps confiáveis;
- acionar overlay;
- criar liberação temporária autorizada.

### OverlayService

Exibe alerta visual quando um acesso monitorado é detectado.

---

## Support Center

O PSBlock inclui uma tela de apoio opcional ao projeto.

A tela contém:

- explicação curta sobre o projeto;
- chave Pix aleatória;
- botão para copiar chave Pix;
- botão para apoiar via PayPal;
- botão GitHub Sponsors;
- aviso de transparência.

O app também exibe um banner discreto de apoio:

- na Dashboard;
- nas Configurações.

O apoio é opcional e não desbloqueia funções artificiais.

---

## Widget

O PSBlock possui widget com estado resumido.

O widget pode indicar:

- protegido;
- vulnerável;
- online;
- standby;
- sensores principais.

---

## Idiomas

O app possui suporte a:

- Inglês;
- Português (Brasil).

A opção de idioma fica em Configurações.

---

## Privacidade e monetização ética

O PSBlock foi feito para ser transparente com a comunidade.

O app:

- não usa SDK de anúncios;
- não usa AdMob;
- não usa interstitial;
- não usa tracking publicitário;
- não vende dados;
- não exige login;
- não envia dados pessoais para monetização;
- não bloqueia recursos atrás de pagamento.

O apoio ao projeto é opcional:

- Pix;
- PayPal;
- GitHub Sponsors.

### Chave Pix

```text
5a8f7456-b245-4b9c-a3ab-64b9d5891434
```

### GitHub Sponsors

```text
https://github.com/sponsors/PSecurity
```

---

## Permissões usadas

O PSBlock usa permissões sensíveis porque atua sobre recursos reais do Android.

| Permissão / grupo | Motivo |
|---|---|
| Câmera | Aplicar bloqueios/fallback de câmera |
| Microfone | Aplicar proteção/fallback de áudio |
| Localização | Zona segura, GPS/localização e políticas automáticas |
| Bluetooth | Controle local de Bluetooth |
| SMS | Comandos autorizados de pânico/wipe remoto |
| Telefone | Políticas relacionadas a chamadas |
| Device Admin | Bloqueio por política, lock e wipe autorizado |
| Acessibilidade | Detecção local de apps em primeiro plano |
| Usage Access | Apoio à leitura de contexto de apps |
| Overlay | Alertas visíveis de proteção |
| Bateria | Manter serviço persistente |
| Internet | Abrir links externos de apoio e GitHub |
| Rede/Wi-Fi | Políticas por rede segura/desconhecida |
| Boot | Reativar proteção após reinicialização |
| Foreground Service | Manter proteção ativa em background |

---

## Instalação

### Opção 1 — APK de release

Quando houver um APK publicado em Releases:

1. Baixe o APK no GitHub Releases.
2. Instale no seu dispositivo Android.
3. Permita instalação de fonte externa, se necessário.
4. Abra o app.
5. Siga o setup inicial de permissões.
6. Configure apenas recursos que você entende e pretende usar.

### Opção 2 — Build local

Clone o repositório e compile pelo Android Studio ou Gradle.

---

## Primeira configuração recomendada

Após instalar:

1. Abra o PSBlock.
2. Toque em **Configurar**.
3. Conceda permissões runtime necessárias.
4. Ative Device Admin se deseja bloqueio real de câmera, lock e wipe autorizado.
5. Configure otimização de bateria para manter o serviço ativo.
6. Ative acessibilidade apenas se deseja monitoramento de apps/sensores.
7. Configure senha local se deseja proteger o console.
8. Teste câmera, microfone, localização e Bluetooth separadamente.
9. Teste o botão **All**.
10. Revise logs.
11. Configure recursos sensíveis, como wipe por SMS, somente se necessário.

---

## Build pelo Android Studio

### Requisitos

- Android Studio recente.
- JDK 17.
- Gradle Wrapper incluído no projeto.
- Android SDK compatível.

Configuração atual do projeto:

| Item | Valor |
|---|---|
| Namespace | `com.psecurity.psblock` |
| Application ID | `com.psecurity.psblock` |
| compileSdk | 34 |
| minSdk | 26 |
| targetSdk | 28 |
| Kotlin | 1.9.24 |
| Android Gradle Plugin | 8.5.2 |
| Java/Kotlin target | 17 |

### Comandos

No Windows:

```bat
gradlew.bat clean --no-build-cache
gradlew.bat assembleDebug --no-build-cache
```

### Abrir no Android Studio

Abra a pasta raiz do projeto, onde ficam:

```text
settings.gradle.kts
build.gradle.kts
app/
gradle/
gradlew.bat
```

Não abra apenas a pasta `app/`.

---

## Estrutura do projeto

Estrutura principal:

```text
PSBlock/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/psecurity/psblock/
│           └── res/
├── gradle/
│   └── wrapper/
├── .github/
├── README.md
├── PRIVACY.md
├── SECURITY.md
├── DISCLAIMER.md
├── SUPPORT.md
├── CHANGELOG.md
├── CONTRIBUTING.md
├── LICENSE
├── NOTICE
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
└── gradlew.bat
```

### Pacotes principais

```text
com.psecurity.psblock
├── MainActivity
├── SettingsActivity
├── SplashActivity
├── LockScreenActivity
├── SecurityGate
├── PSBlockPrefs
├── root/
│   ├── HardwareBlocker
│   └── RootExecutor
├── service/
│   ├── PSBlockService
│   ├── NetworkWatchService
│   ├── AppMonitorService
│   └── OverlayService
├── support/
│   ├── SupportActivity
│   └── SupportConfig
├── receiver/
└── ui/
```

---

## Arquivos que não devem ser enviados ao GitHub

Nunca envie arquivos gerados pelo Android Studio/Gradle.

Não subir:

```text
app/build/
build/
.gradle/
.idea/
local.properties
*.iml
intermediates/
project_dex_archive/
sub_project_dex_archive/
dexBuilderDebug/
tmp/kotlin-classes/
generated/
outputs/
logs/
*.log
```

Subir:

```text
app/src/
app/build.gradle.kts
app/proguard-rules.pro
gradle/wrapper/
README.md
LICENSE
NOTICE
arquivos .md oficiais
settings.gradle.kts
build.gradle.kts
gradle.properties
gradlew
gradlew.bat
```

---

## Limitações importantes

O comportamento pode variar conforme:

- versão do Android;
- fabricante;
- permissões concedidas;
- status de root;
- políticas de bateria;
- restrições do sistema;
- ROM customizada;
- permissões de acessibilidade;
- Device Admin ativo ou não.

Sem root, alguns bloqueios operam em modo fallback e podem não ser tão fortes quanto em root.

Em Android moderno, permissões e restrições variam bastante por fabricante.

---

## Solução de problemas

### Android Studio diz que não é projeto Gradle

Abra a pasta correta, onde existem:

```text
settings.gradle.kts
build.gradle.kts
app/build.gradle.kts
```

Não abra a pasta de fora nem apenas `app/`.

### Erro de classe duplicada no D8 / Dex

Apague apenas arquivos gerados:

```bat
rmdir /s /q app\build
rmdir /s /q build
rmdir /s /q .gradle
gradlew.bat clean --no-build-cache
gradlew.bat assembleDebug --no-build-cache
```

Nunca copie `app/build` ou `.gradle` entre versões.

### Bloqueio de câmera não funciona em todos os apps

Verifique:

- Device Admin ativo;
- permissão de câmera;
- serviço foreground ativo;
- modo root, se disponível;
- restrições específicas do fabricante.

### Microfone ainda parece ativo

Verifique:

- permissão de microfone;
- root disponível ou fallback;
- apps com prioridade de áudio;
- versão do Android;
- logs do PSBlock.

### Serviços param em background

Verifique:

- otimização de bateria;
- permissão de foreground service;
- política do fabricante;
- app em lista de apps sem restrição de bateria.

### Wipe remoto não funciona

Verifique:

- recurso ativado;
- código correto;
- número autorizado;
- permissão SMS;
- Device Admin ativo.

---

## Contribuição

Contribuições são bem-vindas, desde que respeitem a filosofia do projeto.

Antes de contribuir:

- não remova funções existentes sem discussão;
- não enfraqueça proteções;
- não adicione SDK de anúncios;
- não adicione tracking;
- não adicione coleta de dados;
- não altere permissões sensíveis sem justificar;
- não suba arquivos de build/cache;
- teste o build local.

Comando de validação:

```bat
gradlew.bat clean --no-build-cache
gradlew.bat assembleDebug --no-build-cache
```

---

## Apoiar a PeekSecurity

O PSBlock é gratuito, open source e mantido pela PeekSecurity.

Se o projeto te ajuda, você pode apoiar de forma opcional:

### Pix

```text
5a8f7456-b245-4b9c-a3ab-64b9d5891434
```

### PayPal

```text
https://www.paypal.com/donate/?business=22FE58QPQ23KC&no_recurring=0&item_name=Apoie+os+projetos+open+source+da+PeekSecurity+no+GitHub.+Sua+contribui%C3%A7%C3%A3o+mant%C3%A9m+ferramentas+gratuitas+para+a+comunidade.&currency_code=BRL
```

### GitHub Sponsors

```text
https://github.com/sponsors/PSecurity
```

---

## Licença

Este projeto é distribuído sob a licença **Apache License 2.0**.

Consulte:

- [LICENSE](LICENSE)
- [NOTICE](NOTICE)

---

## Aviso de responsabilidade

O PSBlock é fornecido para estudo, privacidade, segurança pessoal e uso autorizado.

Não use o app para interferir em dispositivos de terceiros, ocultar atividade maliciosa, burlar políticas de ambientes que você não controla ou executar ações sem autorização.

Você é responsável pelo uso, configuração e testes no seu próprio dispositivo.
