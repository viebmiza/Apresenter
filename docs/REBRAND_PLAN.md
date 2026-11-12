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
- [x] Criar o commit inicial de linha de base (`60cd452`) e o primeiro commit do Apresenter (`67d7a2b`), pronto para envio à `main` no GitHub.
- [ ] Instalar ferramentas complementares no Windows (se ainda desejar): **VLC** (mídia) e **GitHub CLI (`gh`)** (releases e automação).

---

## Fase 1 — Desacoplamento de Serviços Externos e Telemetria (Concluída em `67d7a2b`)

Impede que versões locais ou instaladas nas igrejas enviem dados de erro, telemetria ou busquem atualizações nos servidores do ChurchPresenter original.

- [x] **Sentry (Crash Reporting):**
  - `composeApp/src/jvmMain/resources/sentry.properties`: `dsn=` zerado para desativar envio ao Sentry oficial até configurar um DSN próprio.
  - `composeApp/build.gradle.kts` (bloco `sentry { ... }`): desacoplado via variáveis de ambiente (`SENTRY_ORG` / `SENTRY_PROJECT`, fallback `apresenter` / `apresenter-desktop`).
- [x] **Telemetria / Mapa ao Vivo / Contato:**
  - `telemetry/src/main/kotlin/org/churchpresenter/telemetry/LiveMapReporter.kt`: desativa envio quando `BrandConfig.PING_URL` está vazio.
  - `telemetry/src/main/kotlin/org/churchpresenter/telemetry/ContactReporter.kt`: usa `BrandConfig.CONTACT_API_URL` (retorna erro de rede controlado enquanto vazio) e `User-Agent` `Apresenter/...`.
- [x] **Atualizador Automático (`:updater`):**
  - `updater/src/main/kotlin/org/churchpresenter/updater/UpdateChecker.kt`, `UpdateAvailableDialog.kt`, `UpdateInstall.kt`:
    - Apontado para `viebmiza/Apresenter` no GitHub Releases e preparado para manifesto customizado (`BrandConfig.CUSTOM_UPDATE_MANIFEST_URL`) quando o site próprio estiver pronto.
    - Suporte a download e execução direta de instalador **`.exe` ou `.msi`** (sem Microsoft Store), com beacon de download opcional via `BrandConfig.DOWNLOAD_BEACON_URL`.
- [x] **Links de Ajuda, Issues e Pacotes Remotos:**
  - `dialogs/src/main/kotlin/org/churchpresenter/dialogs/AboutDialog.kt`: `BUG_REPORT_URL` e `FEATURE_REQUEST_URL` apontados para `viebmiza/Apresenter`.
  - `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/MainWindowChrome.kt`: links do site centralizados em `BrandConfig.websitePageUrl(...)`.
  - `helper/src/main/kotlin/org/churchpresenter/helper/pack/WickPacks.kt`: apontado para `viebmiza/Apresenter` via `BrandConfig.WICK_PACK_URL`.
  - `signage-core/src/main/kotlin/org/churchpresenter/signage/hub/AgentUpdates.kt`: apontado para `viebmiza/Apresenter-Signage`.
  - `bible-formats/.../BebliaCatalogIndex.kt`: mantido temporariamente para não quebrar o catálogo de Bíblias.

---

## Fase 2 — Identidade do Sistema, Pastas de Dados e Portas (Concluída em `67d7a2b`)

Garante que o **Apresenter** possa ser instalado e executado no mesmo computador que o ChurchPresenter original sem sobrescrever configurações, bibliotecas ou instaladores.

- [x] **Criar constante central de identidade (`BrandConfig`):**
  - Criado `settings/src/main/kotlin/org/churchpresenter/settings/utils/BrandConfig.kt` (`APP_NAME = "Apresenter"`, `ASSISTANT_NAME = "Timóteo"`, `AUTHOR_NAME = "Mizael G Nascimento"`, `HOME_FOLDER = ".apresenter"`, `SINGLE_INSTANCE_PORT = 47642`, `GITHUB_OWNER = "viebmiza"`, `GITHUB_REPO = "Apresenter"` e URLs opcionais do futuro site).
- [x] **Migrar pastas de dados em disco (`.churchpresenter` -> `.apresenter`):**
  - `AppDataDir.kt` e todos os módulos que referenciavam `~/.churchpresenter` migrados para `.apresenter`.
- [x] **Identificadores de OS, Rede e Empacotamento:**
  - `composeApp/build.gradle.kts`: `packageName = "Apresenter"`, `vendor = "Mizael G Nascimento"`, `bundleID = "org.apresenter.app"`, `windows.menuGroup = "Apresenter"`, `windows.upgradeUuid = "8F4C2A1E-9B7D-4E31-B2A5-6C8D9E0F1234"`.
  - `Constants.kt`, `SslCertificateManager.kt`, `CertificateRoutes.kt`, `TunnelManager.kt`, `WebsitePresenter.kt`, `AutoStartManager.kt`, `Main.kt` (`FileKit.init(appId = "Apresenter")`) e `cp.app-signing.gradle.kts` atualizados.
  - *(Nota técnica: `rootProject.name = "churchpresenter"` foi mantido em `settings.gradle.kts` para preservar o namespace gerado `churchpresenter.composeapp.generated.resources.Res`.)*

---

## Fase 3 — Identidade Visual (Logo, Ícones e Tema)

- [ ] **Ícones e Logo (opcional/futura, a partir de `C:\dev\Apresenter\branding\logo\`):**
  - Trocar ícones padrão do app quando o Mizael fornecer a logo final do **Apresenter**.
- [x] **Paleta de Cores e Tema (`:theme`):**
  - Decisão do produto: **manter** o módulo `:theme` e o assistente inicial originais, deixando apenas nome, links e recursos sob a marca **Apresenter**.

---

## Fase 4 — Textos, EULA, Assistente e Idioma (Parcialmente Concluída em `67d7a2b`)

- [x] **Strings principais (`:strings`):**
  - Atualizado `app_name`, referências a `ChurchPresenter` / `Church Presenter` -> `Apresenter` e assistente `Wick` -> **Timóteo** em `values/strings.xml` e `values-pt/strings.xml`, além do catálogo semântico gerado por `:helper:updateWickCatalog`.
- [ ] **Módulos auxiliares e outros idiomas:**
  - Revisar `converter_strings*.properties`, `lottiegen_strings*.properties` e demais idiomas conforme necessário.
- [x] **Termos de Uso / EULA e Sobre:**
  - Atualizado `composeApp/src/jvmMain/composeResources/files/eula.txt` e diálogos (`AboutDialog.kt`, `AlreadyRunningDialog.kt`, etc.) preservando créditos e licença **GNU GPL v3**.
- [ ] **Experiência pronta para Igrejas Brasileiras (sugestões a confirmar após testes):**
  - Opcionalmente pré-empacotar ou facilitar o download de Bíblia em Português no Setup Wizard.

---

## Fase 5 — Customizações e Melhorias Funcionais (Em Andamento)

- [x] **Novo Recurso — Pesquisa de Músicas na Internet (`:songs`):**
  - Implementados `OnlineSongSearchClient.kt`, `OnlineSongLyricsFormatter.kt`, `OnlineSongSearchDialog.kt` e `OnlineSongSearchParts.kt`, permitindo pesquisar por título, artista ou trecho da letra (LRCLIB + fallback iTunes/Lyrics.ovh), visualizar os slides formatados e importar direto na biblioteca.
- [ ] **Futuro site próprio:**
  - Preencher `WEBSITE_URL`, `CONTACT_API_URL`, `CUSTOM_UPDATE_MANIFEST_URL` (se usar site além do GitHub Releases) em `BrandConfig.kt` quando o domínio/site estiver no ar.
- [ ] **Simplificações e melhorias após testes reais:**
  - Avaliar durante o uso quais abas manter/ocultar por padrão para operadores iniciantes e quais melhorias adicionais criar.
- [ ] **Aplicativo Android (`.apk`) e futuro iOS:**
  - Criar ou importar o repositório mobile separado (equivalente ao companion app na porta `8765`, já que este repositório contém apenas o aplicativo Desktop JVM e o servidor local `:server`).

---

## Fase 6 — Validação, CI/CD e Distribuição Profissional

- [x] Verificações principais executadas após o commit `67d7a2b`: `checkModuleLayers`, `detekt` e suítes unitárias/UI dos módulos alterados.
- [ ] Rodar suíte completa em CI e gerar instalador **`.exe` / `.msi`** assinado (ou via GitHub Actions `.github/workflows/build.yml` + GitHub Releases em `viebmiza/Apresenter`) para distribuição nas igrejas e auto-atualização sem Microsoft Store.
- [ ] Validar fluxo ponta a ponta do auto-updater (`Verificar Atualizações` -> download com verificação SHA-256 -> execução `.exe` / `msiexec /i`).
- [ ] (Opcional/Recomendado para produção) Configurar certificado de assinatura de código (Code Signing) no Windows para evitar alertas do Windows Defender SmartScreen nas igrejas.

