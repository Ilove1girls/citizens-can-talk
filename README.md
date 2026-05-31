# Citizens Can Talk

A fork of [Talking Colonists](https://github.com/sshcrack/talking-colonists) by [sshcrack](https://github.com/sshcrack) that replaces the cloud-only AI backend with cheaper, self-hostable alternatives.

Talk to your MineColonies citizens as if they were real people — now with local voices and affordable AI.

## What's Different?

| | Original (Talking Colonists) | This Fork |
|---|---|---|
| **AI Backend** | Gemini Live API | DeepSeek Chat API (OpenAI-compatible) |
| **Speech-to-Text** | Built into Gemini Live | OpenAI Whisper (or compatible) |
| **Text-to-Speech** | None (text only) | **Piper** (local, fast) or **Kokoro** (local, natural) |
| **Cost** | Gemini API usage | DeepSeek is ~10× cheaper; TTS/STT run locally for free |
| **Voice Chat** | Required | Required (Simple Voice Chat) |

## Setup

### 1. Install Dependencies
- [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies)
- [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat)
- This mod

### 2. Get API Keys

**DeepSeek** (for citizen AI):
1. Go to [platform.deepseek.com](https://platform.deepseek.com) and create an API key.
2. In-game, run `/cct_config` or edit `config/yacl-mc_talking.json5` directly.
3. Set `deepseekApiKey` to your key.

**Whisper** (for speech-to-text):
1. Get an OpenAI API key at [platform.openai.com](https://platform.openai.com).
2. Set `whisperApiKey` in the same config.

> **Tip:** Both services have very generous free tiers. DeepSeek in particular is extremely cheap — typically pennies per hour of gameplay.

### 3. Configure TTS Engine (Optional)

By default, the mod uses **Piper TTS** (fast, runs entirely offline). To switch:
- Open the in-game config (`/cct_config`)
- Change `TTS Engine` to `KOKORO` for more natural-sounding voices
- Models download automatically on first use

### 4. Toggle Features

| Command | What it does |
|---|---|
| `/cct_debug on\|off` | Enable/disable debug logging |
| `/cct_chat on\|off` | Show/hide citizen speech in Minecraft chat (voice always plays) |

## Usage

1. Craft a **Citizen Communication Device** (Book and Quill + Redstone Torch).
2. Left-click a citizen while holding the device.
3. Talk into voice chat — your speech is transcribed, sent to DeepSeek, and the citizen responds with a synthesized voice.

Citizens remember conversations, have personalities tied to their jobs, and can get angry, scared, or happy depending on colony conditions.

## Credits

- **Original mod:** [Talking Colonists](https://github.com/sshcrack/talking-colonists) by [sshcrack](https://github.com/sshcrack) / Hendrik Lind
- **AI:** [DeepSeek](https://deepseek.com)
- **Local TTS:** [Piper](https://github.com/rhasspy/piper) (via sherpa-onnx) and [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M) (ONNX Runtime)
- **STT:** [OpenAI Whisper](https://openai.com/research/whisper)
- **Libraries:** [MineColonies](https://github.com/ldtteam/minecolonies), [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat), [YACL](https://github.com/isXander/YetAnotherConfigLib)

## License

This fork is derived from the original Talking Colonists mod, which is licensed under the [CoFH "Don't Be a Jerk" License](LICENSE). All original code and assets remain the property of Hendrik Lind. Modifications in this fork are provided under the same license terms.

## Can I include this in my modpack?

Check with the original author first — the original mod says "Yup," but this fork adds additional dependencies and services that may not suit all pack types.
