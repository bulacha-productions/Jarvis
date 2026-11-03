# 🤖 Jarvis Assistant — V2.0.0 (Sistema Multiagente & Correção Gemini)

Jarvis é um assistente de inteligência artificial nativo para Android (Kotlin + Jetpack Compose + Material 3) com arquitetura Multiagente, persistência Room e suporte a múltiplos provedores de IA (Google Gemini, OpenAI, Claude).

## 🚀 Novidades da V2.0.0

- 🛠️ **Correção Definitiva da API Google Gemini (HTTP 404):** Consulta dinâmica via `models.list`, filtrando por `supportedGenerationMethods` com `generateContent`, remoção de prefixos `models/` redundantes, sanitização de histórico e alternância de turnos.
- 🧠 **Arquitetura Multiagente (Jarvis Coordinator):**
  - **⚡ Jarvis Central:** Planejamento, decomposição de intenções e síntese unificada.
  - **💻 Agente de Programação:** Engenharia de software, código limpo e depuração.
  - **🔍 Agente de Pesquisa:** Investigação de fontes, fatos e síntese temática.
  - **✨ Agente Criativo:** Narrativas, redação e ideação conceitual.
  - **📊 Agente Analítico:** Lógica dedutiva, matemática e validação algorítmica.
- 🔄 **Colaboração Interagente com Prevenção Anti-Loop (Máximo 3 etapas).**
- 🏷️ **Badges Visuais no Chat:** Indicação transparente dos agentes que participaram da resposta.
- 🎙️ **Ciclo de Vida de Áudio Preservado:** SpeechRecognizer repetível e contínuo.
- ➕ **Menu (+) com Câmera, Fotos, Arquivos e Capacidade por Mensagem.**
- 📦 **Download Direto do APK via GitHub Actions Releases.**
