# Guia Prático: Usando STT Local com Whisper no Apresenter

> **Recurso avançado (opcional):** A aba **STT** (*Speech-to-Text*, legendas ao vivo) vem oculta por padrão nas configurações de visibilidade de abas porque requer captação limpa do microfone da pregação. Este guia explica como ligá-la localmente usando **Python + OpenAI Whisper**, sem depender de serviços pagos na nuvem.

---

## 1. Como captar apenas a voz do pastor na igreja

Em um culto real, **não use o microfone embutido do notebook/PC** da mídia: ele capta barulho da sala, teclado, retorno das caixas e gera transcrições erradas.

O arranjo recomendado na igreja é:

1. **Mesa de som → Saída separada (`AUX`, `Matrix` ou `Subgrupo`):** peça ao sonoplasta para enviar para uma saída auxiliar **somente os canais de microfone de fala** (púlpito, lapela, headset), deixando banda e músicas de fora.
2. **Mesa de som → Computador:** ligue essa saída auxiliar em uma **interface de áudio USB** (ou na entrada `Line-In` do computador).
3. **Somente na hora da Palavra:** na aba **STT**, clique em **Transmitir** apenas quando iniciar a mensagem.

*(Para testes rápidos em casa ou no escritório, você pode usar o microfone normal do computador.)*

---

## 2. Pré-requisitos no computador

Você precisa de **Python 3.10 a 3.13** instalado e das bibliotecas abaixo:

```powershell
pip install openai-whisper sounddevice numpy
```

*(O script ponte [tools/whisper_stt_server.py](../tools/whisper_stt_server.py) implementa o protocolo WebSocket/Socket.IO v4 usando apenas a biblioteca padrão do Python — não precisa instalar `python-socketio` nem Node.js.)*

### Qual modelo Whisper escolher?
- **`tiny`**: bem rápido em processador comum (CPU), ótimo para testar ou computadores mais simples (~75 MB).
- **`base`** ou **`small`**: bem mais precisos em português e termos bíblicos (~483 MB no `small`), exigindo um pouco mais de CPU (ou placa de vídeo NVIDIA com CUDA).

---

## 3. Descobrir qual entrada de áudio usar

No PowerShell ou Terminal, dentro da pasta `C:\dev\Apresenter`, liste as entradas disponíveis:

```powershell
python tools/whisper_stt_server.py --list-devices
```

Anote o número `[N]` correspondente à sua **interface de áudio USB** ou entrada **Line-In** ligada na mesa de som (ou use o padrão apenas para testes).

---

## 4. Iniciar o servidor STT local

### Teste rápido no microfone padrão (modelo rápido `tiny`):
```powershell
python tools/whisper_stt_server.py --model tiny --language pt
```

### Uso recomendado com placa da mesa de som (modelo `small`):
```powershell
python tools/whisper_stt_server.py --model small --language pt --device 1 --port 8766
```

### Se o Whisper estiver rodando em outro computador na rede da igreja:
Use `--host 0.0.0.0` no computador que recebe o áudio da mesa de som:
```powershell
python tools/whisper_stt_server.py --model small --language pt --device 1 --host 0.0.0.0 --port 8766
```

Parâmetros úteis:
- `--chunk-seconds 3.5`: tamanho de cada bloco de fala enviado para transcrição (blocos menores respondem mais rápido; maiores dão mais contexto para a frase).
- `--threshold 0.008`: nível mínimo de volume (RMS) para ignorar silêncio entre frases.
- Para encerrar o servidor, aperte **`Ctrl + C`** no terminal.

---

## 5. Conectar dentro do Apresenter

1. Se a aba **STT** ainda estiver oculta, ative-a nas configurações de visibilidade das abas do Apresenter.
2. No campo **URL do Servidor STT**, informe:
   - `http://127.0.0.1:8766` (se estiver rodando no mesmo computador), **ou**
   - `http://IP_DO_OUTRO_PC:8766` (se estiver em outro computador da mesma rede).
3. Clique em **Conectar**.
4. Quando as frases começarem a aparecer em **Pré-visualização ao Vivo**, clique em **Transmitir** para projetar as legendas nas saídas configuradas (e ajuste fonte/posição em **Configurações de Exibição do STT**).

---

## 6. Referência técnica do protocolo (`stt-engine`)

O módulo receptor no Apresenter (`stt-engine/src/main/kotlin/org/churchpresenter/stt/STTManager.kt` e `STTTranscript.kt`) espera um servidor compatível com **Socket.IO v4 sobre WebSocket** (`/socket.io/?EIO=4&transport=websocket`):

- **Eventos recebidos pelo Apresenter:**
  - `transcription_update`: `{ "session_id", "segments": [{ "id", "timestamp", "text", "start", "end", "completed" }], "in_progress" }`
  - `translation_update`: `{ "session_id", "segments": [{ "id", "timestamp", "translated_text", "start", "end", "completed" }], "in_progress", "target_language_name" }`
  - `word_highlighting_update`: `{ "enabled", "disabled_colors", "words" }`
- **Eventos enviados pelo Apresenter ao conectar:** `request_all_entries`, `request_all_translation_entries`, além de `GET /api/word-highlighting/words`.
