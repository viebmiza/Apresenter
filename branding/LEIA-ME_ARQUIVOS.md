# Pasta de Arquivos da Marca — Apresenter

Coloque aqui apenas os **arquivos-fonte principais** da sua marca (**Apresenter**).
Não precisa exportar manualmente 30 tamanhos diferentes: a partir da logo principal (`1024×1024` + vetor `.svg`), nós geramos automaticamente todas as resoluções exigidas pelo Windows, macOS, Linux, barras de janela e Microsoft Store.

---

## 1. O que você precisa colocar aqui (Arquivos que você prepara)

### Pasta `branding\logo\` (Obrigatórios para a identidade visual)

| Arquivo para colocar | Formato | Tamanho / Dimensão | Para que serve e dicas |
|---|---|---|---|
| `logo-icon-1024.png` | PNG (RGBA com transparência) | **1024 × 1024 px** (1:1 quadrado) | **Matriz principal de todos os ícones** (instalador Windows `.ico`, barra de tarefas, Alt+Tab, Linux `.png`, macOS `.icns`/`.iconset` e tiles MSIX). Recomenda-se deixar ~8–10% de margem interna de respiro nas bordas, e garantir boa leitura mesmo quando reduzido para 16×16 px e 32×32 px. |
| `logo-icon.svg` | SVG (vetorial) | ViewBox **512 × 512** (1:1 quadrado) | Usado para converter para `ic_app_icon.xml` (desenhado em alta definição na **Tela de Splash**, **Assistente Inicial**, **Sobre**, **EULA/Licença** e janelas auxiliares). Use traços simples e caminhos sólidos (evite textos sem converter em curvas e evite filtros/sombras CSS complexas). |
| `logo-icon-tray-32.png` *(opcional)* | PNG (RGBA) | **32 × 32 px** (ou **64 × 64 px**) | Apenas se você quiser um desenho simplificado exclusivo para tamanhos minúsculos (16 px / 24 px / 32 px na barra de tarefas do Windows). Se não enviar, reduziremos automaticamente do `logo-icon-1024.png` com filtro Lanczos. |

---

### Pasta `branding\screenshots\` (Opcional — para telas internas)

| Arquivo para colocar | Formato | Tamanho / Dimensão | Para que serve |
|---|---|---|---|
| `preview-dark.png` | PNG (RGBA) | **800 × 500 px** | Substitui `share_story_screenshot_dark.png` (ilustração de tela no tema escuro). |
| `preview-light.png` | PNG (RGBA) | **800 × 500 px** | Substitui `share_story_screenshot_light.png` (ilustração de tela no tema claro). |

*(Nota: se você optar por remover ou reformular a janela "Compartilhe sua história" na Fase 5, estes dois arquivos nem serão necessários — ou podemos gerar os prints reais do próprio **Apresenter** depois que o tema novo estiver aplicado!)*

---

### Pasta `branding\docs\` (Opcional — referência da marca e projeto social)

| Arquivo para colocar | Formato | Conteúdo sugerido |
|---|---|---|
| `paleta-cores.txt` (ou `.md` / `.pdf`) | Texto / PDF | Códigos HEX das cores da sua marca (cor principal/acento, cor secundária, fundo escuro/claro desejado) para aplicarmos no tema padrão em `:theme`. |
| `projeto-social.txt` (ou `.md`) | Texto | Nome do projeto social, site/Instagram/WhatsApp de suporte para as igrejas e texto que deseja exibir na janela **Sobre** e no **EULA**. |

---

## 2. O que será gerado automaticamente no projeto a partir da sua logo

Quando você colocar os arquivos em `branding\logo\`, nós atualizaremos/geraremos automaticamente todos estes arquivos do código-fonte:

| Destino no projeto | Tamanhos / Formatos gerados | Onde aparece |
|---|---|---|
| `composeApp/src/jvmMain/appResources/windows/icon.ico` | Multi-resolução: `16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 128, 256 px` | Instalador `.msi`/`.exe`, atalho da Área de Trabalho, Menu Iniciar e Barra de Tarefas |
| `icons/src/main/resources/app-icon/icon-*.png` | `16, 20, 24, 32, 40, 48, 64, 128, 256 px` | Barra de título das janelas do Windows e `Alt+Tab` |
| `composeApp/src/jvmMain/appResources/common/icon-*.png` | `16, 24, 32, 48, 64, 128, 256, 512 px` | Seletor de arquivos nativo e recursos comuns |
| `composeApp/src/jvmMain/appResources/linux/icon.png` | `512 × 512 px` | Pacote `.deb` / Linux |
| `composeApp/src/jvmMain/appResources/macos/icon.png` + `icon.iconset/` + `icon.icns` | `16, 32, 64, 128, 256, 512, 1024 px` | Bundle `.app` e `.dmg` no macOS |
| `composeApp/src/msix/Assets/` | `StoreLogo.png` (**50×50**), `Square44x44Logo.png` (**44×44**), `Square150x150Logo.png` (**150×150**) | Pacote Windows MSIX / Microsoft Store |
| `icons/src/main/composeResources/drawable/ic_app_icon.xml` | Vetor Android/Compose (`512dp × 512dp`) | Splash, Setup Wizard, Sobre, Licença e janela de saída |
| `icons/src/main/composeResources/drawable/share_story_screenshot_*.png` | `800 × 500 px` (claro e escuro) | Janela de história/boas-vindas (se mantida) |

