# DeepSeek Migration Tracker

Migrating Talking Colonists from Gemini Live to DeepSeek + external STT/TTS.

## Architecture

```
Player Voice (Opus) → STT (Whisper) → Text → DeepSeek Chat → Text Response → Minecraft Chat
                                      ↓
                              Citizen Tools (function calling)
```

## Task List

### Phase 1: STT + DeepSeek Chat (MVP)
- [x] Create task tracker
- [x] Understand voice chat audio pipeline
- [x] Add HTTP client dependency (using Java 11 `java.net.http.HttpClient`)
- [x] Create `WhisperSttClient` — sends audio bytes to Whisper API, returns transcript
- [x] Create `DeepSeekChatClient` — sends chat messages to DeepSeek, returns response (OpenAI-compatible)
- [x] Create `DeepSeekCitizenClient` — replaces `CitizenWsClient` for DeepSeek flow
  - [x] Buffer incoming audio until silence threshold or max duration
  - [x] Send buffered audio to Whisper
  - [x] Send transcript + system prompt + memory to DeepSeek
  - [x] Display DeepSeek response in Minecraft chat as citizen speech
  - [ ] Support citizen tools via DeepSeek tool calling (OpenAI format) — skipped for MVP
- [x] Update `McTalkingConfig` — add `deepseekApiKey`, `deepseekModel`, `whisperApiKey`, `useDeepSeek`
- [x] Update `ConversationManager` to use `DeepSeekCitizenClient` when `useDeepSeek = true`
- [x] Update `McTalkingVoicechatPlugin` to skip silence injection for DeepSeek clients
- [x] Create `CitizenAiClient` interface to abstract over Gemini and DeepSeek
- [ ] Build & test — `./gradlew build`, `runClient`, verify voice → chat flow

### Phase 2: TTS (Future)
- [ ] Integrate external TTS (Piper / Coqui / Azure TTS)
- [ ] Stream TTS audio back through voice chat
- [ ] Support multi-speaker voices for citizen-to-citizen conversations

### Phase 3: Polish
- [ ] Memory extraction via DeepSeek (replace `GeminiFlash` in memory generators)
- [ ] Conversation generation via DeepSeek (replace `GeminiFlash` in `CitizenConversationGenerator`)
- [ ] Remove `gemini_live_lib` dependency entirely
- [ ] Clean up unused Gemini-specific code

## Notes
- DeepSeek API is OpenAI-compatible: `https://api.deepseek.com/chat/completions`
- Whisper API: `https://api.openai.com/v1/audio/transcriptions`
- Both use standard HTTP POST with JSON responses
- Tool calling: DeepSeek supports OpenAI-style `tools` parameter
