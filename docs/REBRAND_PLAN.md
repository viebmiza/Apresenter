# Plano de Rebranding e Estrutura Profissional — Apresenter

> **Projeto:** Apresenter (derivado de ChurchPresenter sob GNU GPL v3)
> **Responsável / Autor:** Mizael G Nascimento (`viebmiza` / `raquelanascimento@gmail.com`)
> **Repositório GitHub:** `https://github.com/viebmiza/Apresenter.git`
> **Diretório local:** `C:\dev\Apresenter`
> **Pasta de materiais da marca:** `C:\dev\Apresenter\branding\` (ver `branding\LEIA-ME_ARQUIVOS.md`)
> **Objetivo:** Transformar a base de código em um produto com marca própria (**Apresenter**), arquitetura limpa para customizações contínuas e pipeline profissional de distribuição e atualização automática para igrejas do projeto social.

---

## Princípios de Engenharia que Vamos Seguir

1. **Nunca quebrar o build entre etapas:** Cada fase termina com compilação (`:composeApp:compileKotlinJvm`), `checkModuleLayers` e `detekt` passando.
2. **Centralizar identidade antes de editar dezenas de arquivos:** Em vez de sair fazendo "localizar e substituir" cego, criaremos um ponto único de configuração da marca (`BrandConfig`) e migraremos referências aos poucos.
3. **Preservar os pacotes internos (`org.churchpresenter.*`) no início:** Renomear o namespace Java/Kotlin de 50+ módulos no Dia 1 gera conflitos desnecessários e quebra testes por reflexão (`ThemeKt`). Faremos isso só no final, se desejado.
4. **Conformidade com GPL v3:** Como o original é `GNU GPL v3` (`LICENSE.txt`), o **Apresenter** mantém a licença GPL v3, preserva os avisos de copyright originais indicando ser uma versão modificada por Mizael G Nascimento, e mantém o código-fonte acessível a quem receber o software.

---

## Fase 0 — Ambiente, Git e Fundação (Pré-requisito)

- [x] Mover projeto para fora do OneDrive: `C:\dev\Apresenter`.
- [x] Configurar `JAVA_HOME` (JDK 21) e otimizar `C:\Users\jgame\.gradle\gradle.properties` para 8 GB de RAM.
- [x] Inicializar Git local em `C:\dev\Apresenter` com `core.hooksPath = .githooks`, autor `Mizael G Nascimento` e e-mail `raquelanascimento@gmail.com`.
- [x] Configurar remote `origin` (`https://github.com/viebmiza/Apresenter.git`) e `upstream` (`ChurchPresenter/ChurchPresenter`).
- [ ] Criar o commit inicial de linha de base (`baseline`) e enviar a `main` inicial ao GitHub.
- [ ] Instalar ferramentas complementares no Windows: **VLC** (mídia) e **GitHub CLI (`gh`)** (releases e automação).

---

## Fase 1 — Desacoplamento de Serviços Externos e Telemetria (Prioridade Máxima)

Impede que versões locais ou instaladas nas igrejas enviem dados de erro, telemetria ou busquem atualizações nos servidores do ChurchPresenter original.

- [ ] **Sentry (Crash Reporting):**
  - `composeApp/src/jvmMain/resources/sentry.properties`: remover o DSN oficial; usar DSN próprio ou deixar vazio para desativar envio externo.
  - `composeApp/build.gradle.kts` (bloco `sentry { ... }`): atualizar `org` e `projectName` para o seu Sentry (ou desabilitar upload de source context).
- [ ] **Telemetria / Mapa ao Vivo / Contato:**
  - `telemetry/src/main/kotlin/org/churchpresenter/telemetry/LiveMapReporter.kt`: desativar `PING_URL` (`churchpresenter.org/api/ping`) por padrão ou apontar para endpoint próprio.
  - `telemetry/src/main/kotlin/org/churchpresenter/telemetry/ContactReporter.kt`: trocar `WEB_CONTACT_URL` e `ENDPOINT` pelo canal de suporte do seu projeto social (ou ocultar o formulário remoto caso não haja backend).
- [ ] **Atualizador Automático (`:updater`):**
  - `updater/src/main/kotlin/org/churchpresenter/updater/UpdateChecker.kt`:
    - Alterar `RELEASES_API_URL`, `RELEASES_URL` e `PULLS_URL` para `https://api.github.com/repos/viebmiza/Apresenter/releases?per_page=50`, `https://github.com/viebmiza/Apresenter/releases/latest` e `https://github.com/viebmiza/Apresenter/pull`.
    - Remover ou apontar `DOWNLOAD_BEACON_URL` (`churchpresenter.org/api/download`) para servidor próprio.
    - Atualizar `User-Agent` para `Apresenter/...`.
- [ ] **Links de Ajuda, Issues e Pacotes Remotos:**
  - `dialogs/src/main/kotlin/org/churchpresenter/dialogs/AboutDialog.kt`: atualizar `BUG_REPORT_URL` e `FEATURE_REQUEST_URL` para `https://github.com/viebmiza/Apresenter/issues/new?...`.
  - `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/MainWindowChrome.kt`: atualizar `onHelp`, `onHowToBlog` e `onTutorialVideos` para links do **Apresenter** (ou página do projeto social).
  - `helper/src/main/kotlin/org/churchpresenter/helper/pack/WickPacks.kt`: apontar `WICK_PACK_URL` para `https://raw.githubusercontent.com/viebmiza/Apresenter/main/wick-pack/pack.json`.
  - `signage-core/src/main/kotlin/org/churchpresenter/signage/hub/AgentUpdates.kt`: atualizar `RELEASE_URL` se for utilizar o módulo Signage.
  - `bible-formats/src/main/kotlin/org/churchpresenter/bibleformats/catalog/BebliaCatalogIndex.kt`: **manter** `OWNER = "ChurchPresenter"` temporariamente (ou fazer fork/espelho do repositório `Holy-Bible-XML-Format`) para não quebrar o download de Bíblias.

---

## Fase 2 — Identidade do Sistema, Pastas de Dados e Portas (Coexistência Limpa)

Garante que o **Apresenter** possa ser instalado e executado no mesmo computador que o ChurchPresenter original sem sobrescrever configurações, bibliotecas ou instaladores.

- [ ] **Criar constante central de identidade (`BrandConfig`):**
  - Centralizar em `:settings` ou `:core-models` (e/ou injetar via `cp.app-provenance.gradle.kts`): nome exibido (`Apresenter`), pasta oculta (`.apresenter`), pasta AppData (`Apresenter`), autor (`Mizael G Nascimento`), repositório GitHub.
- [ ] **Migrar pastas de dados em disco (`.churchpresenter` -> `.apresenter`):**
  - `settings/src/main/kotlin/org/churchpresenter/settings/utils/AppDataDir.kt` (`HOME_FOLDER` e `APP_FOLDER`).
  - Padronizar todos os módulos que acessam `.churchpresenter` diretamente via `System.getProperty("user.home")`:
    - `app-settings`: `SystemSettingsActions.kt`
    - `bible-engine`: `AppConfig.kt`
    - `bible-formats`: `BebliaCatalogIndex.kt`, `EBibleSource.kt`, `ZefaniaRepositoryIndex.kt`
    - `bible-tab`: `BibleEngineClient.kt`, `BibleViewModel.kt`, `VerseSequenceLog.kt`
    - `canvas` / `canvas-engine`: `SceneViewModel.kt`, `DeckLinkManager.kt`
    - `composeApp`: `RemoteApply.kt`, `RemoteMirroring.kt`
    - `core-models`: `SongFileParser.kt`
    - `diagnostics`: `CrashReporter.kt`
    - `dialogs`: `AboutDialog.kt`
    - `helper`: `WickPacks.kt`
    - `live-output`: `OmtManager.kt`
    - `lottieGenerator`: `BandConfigStorage.kt`, `ColorThemeStorage.kt`, `LogoStorage.kt`, `PresetStorage.kt`, `StyleSpecStorage.kt`, `ColorPickerDialog.kt`
    - `lower-third-engine`: `LottieCacheFiles.kt`, `LottieRenderCache.kt`
    - `media` / `media-engine`: `StockMediaClient.kt`, `RecentMediaFiles.kt`, `VideoFirstFrame.kt`
    - `output-guard`: `GuardFiles.kt`
    - `planning-center`: `PlanningCenterImportViewModel.kt`
    - `presentation-engine`: `SlideDiskCache.kt`
    - `profiles`: `BibleLottieBandPicker.kt`, `LocalLibraryDialog.kt`, `SongBackgroundSwatches.kt`
    - `qa`: `QAStore.kt`
    - `schedule`: `ScheduleViewModel.kt`
    - `server` / `server-ui`: `InstanceLinkLogger.kt`, `MediaAndAssetRoutes.kt`, `PictureLibrary.kt`, `PresentationRoutes.kt`, `PresentationStore.kt`, `SslCertificateManager.kt`, `TunnelManager.kt`, `RemoteClientManager.kt`
    - `shared-ui`: `LabelColors.kt`, `RecentColors.kt`, `SavedTextBackdrops.kt`, `LiveHistoryLogger.kt`, `TrainingDataLogger.kt`, `UsageEvents.kt`
    - `slides`: `HiddenItemsStore.kt`, `RecentPresentationFiles.kt`, `RecentPictureFolders.kt`, `PicturesViewModel.kt`, `PresentationViewModel.kt`
    - `statistics`: `StatisticsManager.kt`
    - `stt-engine`: `STTCapture.kt`
    - `web-engine`: `WebsitePresenter.kt`
- [ ] **Identificadores de OS, Rede e Empacotamento:**
  - `composeApp/build.gradle.kts`:
    - `packageName = "Apresenter"`
    - `description`, `copyright`, `vendor = "Mizael G Nascimento"`
    - `macOS.bundleID = "br.com.apresenter.app"` (ou domínio escolhido)
    - `windows.menuGroup = "Apresenter"`
    - `windows.upgradeUuid = "<NOVO-UUID-GERADO>"` (obrigatório trocar para não conflitar no registro do Windows)
    - `cyclonedxDirectBom.componentName = "Apresenter"` e `-Xdock:name=Apresenter`
  - `settings/.../Constants.kt`: atualizar `SINGLE_INSTANCE_PORT` (ex.: `47642`), `SERVER_APP_NAME = "Apresenter"`, `SSL_KEY_ALIAS`, `SSL_KEYSTORE_PASSWORD`.
  - `server/.../SslCertificateManager.kt`: `CA_ALIAS = "apresenter-ca"`.
  - `server/.../CompanionServer.kt`: `ADVERT_NAME = "Apresenter"`.
  - `app-settings/.../AutoStartManager.kt`: `APP_NAME = "Apresenter"`, `.plist` e `.desktop`.
  - `composeApp/.../Main.kt`: `FileKit.init(appId = "Apresenter")`.
  - `build-logic/.../cp.app-signing.gradle.kts`: atualizar defaults `msix*` e `winSubjectName`.
  - `settings.gradle.kts`: `rootProject.name = "apresenter"`.

---

## Fase 3 — Identidade Visual (Logo, Ícones e Tema)

- [ ] **Ícones e Logo (a partir de `C:\dev\Apresenter\branding\logo\`):**
  - Ler `branding/logo/logo-icon-1024.png` (1024×1024) e `branding/logo/logo-icon.svg`.
  - Gerar todos os tamanhos de `composeApp/src/jvmMain/appResources/macos/icon.iconset/`, `appResources/common/icon-*.png`, `appResources/linux/icon.png` (512×512), `appResources/macos/icon.png` (512×512) e `appResources/macos/icon.icns`.
  - Rodar `python composeApp/tools/generate_windows_icon.py` para atualizar `windows/icon.ico` e `icons/src/main/resources/app-icon/icon-*.png`.
  - Atualizar `composeApp/src/msix/Assets/` (`StoreLogo.png` 50×50, `Square44x44Logo.png` 44×44, `Square150x150Logo.png` 150×150) e `composeApp/src/msix/AppxManifest.xml`.
  - Converter `logo-icon.svg` para `icons/src/main/composeResources/drawable/ic_app_icon.xml` (Splash, Wizard, Sobre, Licença).
  - Atualizar `icons/src/main/composeResources/drawable/share_story_screenshot_dark.png` e `share_story_screenshot_light.png` (800×500) a partir de `branding/screenshots/`, caso mantidos.
- [ ] **Paleta de Cores e Tema (`:theme`):**
  - Editar `theme/src/main/kotlin/org/churchpresenter/theme/Theme.kt`, `SemanticColors.kt` e `AppShape.kt` (`CORNER_SCALE`).
  - Definir qual tema será o padrão do **Apresenter** na primeira abertura (`AppSettings`).
  - Validar testes do módulo (`.\gradlew.bat :theme:test :theme:detekt`), que checam automaticamente contraste WCAG e rampa de superfície.

---

## Fase 4 — Textos, EULA, Idioma e Experiência Inicial para Igrejas

- [ ] **Strings principais (`:strings`):**
  - Atualizar `app_name` e todas as ~45 ocorrências de `ChurchPresenter` / `Church Presenter` em `strings/src/main/composeResources/values/strings.xml` e `values-pt/strings.xml` (e decidir quais outros idiomas manter/atualizar).
- [ ] **Módulos auxiliares com strings próprias:**
  - `converter/src/main/resources/converter_strings*.properties`
  - `lottieGenerator/src/main/resources/lottiegen_strings*.properties`
  - `calendar/` e `songlibrary/` (verificar `strings.xml` próprios, se aplicável).
- [ ] **Termos de Uso / EULA e Sobre:**
  - Atualizar `composeApp/src/jvmMain/composeResources/files/eula.txt` para **Apresenter — Mizael G Nascimento**, mantendo a seção da licença **GPLv3** e atribuição ao projeto base.
  - Revisar `AboutDialog.kt` (exibir créditos originais + autoria da adaptação por Mizael G Nascimento).
- [ ] **Experiência pronta para Igrejas Brasileiras (sugestões a confirmar após testes):**
  - Idioma inicial padrão em Português (`pt`) quando o sistema estiver em português.
  - Opcionalmente pré-empacotar ou facilitar o download de Bíblia em Português (ex.: Almeida Livre / domínio público) no Setup Wizard.

---

## Fase 5 — Customizações e Melhorias Funcionais (Backlog de Produto)

> Espaço vivo que será preenchido com os feedbacks do Mizael após testar o ChurchPresenter original:
- [ ] Definir o que **manter** (Músicas, Bíblia, Roteiro, Apresentações, Mídia, Palco, Controle Remoto, etc.).
- [ ] Definir o que **simplificar ou ocultar** (ex.: abas avançadas como Signage, Crossword, NDI/ATEM/OBS caso não sejam foco inicial das igrejas atendidas).
- [ ] Definir o que **melhorar ou criar** (ex.: interface mais simples para operadores voluntários, presets prontos para projeção, etc.).

---

## Fase 6 — Validação, CI/CD e Distribuição Profissional

- [ ] Rodar gates de qualidade antes de cada release:
  - `.\gradlew.bat checkModuleLayers`
  - `.\gradlew.bat :composeApp:detekt`
  - `.\gradlew.bat :composeApp:check`
- [ ] Configurar GitHub Actions (`.github/workflows/test.yml` e `.github/workflows/build.yml`) no repositório do **Apresenter** para gerar instaladores `.msi` (Windows) com hash SHA-256 automático na GitHub Release.
- [ ] Validar fluxo ponta a ponta do auto-updater (`Verificar Atualizações` -> download com verificação SHA-256 -> `msiexec /i`).
- [ ] (Opcional/Recomendado para produção) Configurar certificado de assinatura de código (Code Signing) no Windows para evitar alertas do Windows Defender SmartScreen nas igrejas.

