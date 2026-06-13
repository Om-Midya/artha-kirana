# Handoff — On-device LLM inference on iQOO 15 via ADB

## TL;DR (do this first)

```bash
adb devices                                # confirm iQOO 15 is attached
cd /Users/archismanmidya/Desktop/CrazyStuff/iqoo-mobile
./qwen.sh "What is the capital of France?"
# → "The capital of France is Paris."
```

If that works, the setup is healthy and you can skip the rest of this doc.

## What the user wants

Run a local LLM on their **iQOO 15** (Snapdragon 8 Elite, 16 GB RAM, Android 16, arm64-v8a) and talk to it from the Mac shell. Two hard constraints they set during the session:

1. **No Termux.** They tried it in a previous session and don't want a 30-min source build. Their words: *"dontdon't use termux, can you connect to adb and execte the commands itself"*.
2. **Reuse what's on the phone.** They had Qwen 2.5 3B, Llama 3B, and Qwen models loaded in PocketPal AI already and wanted to use those.

## Current state — WORKING

- **llama.cpp b9620 prebuilt** (Android arm64) extracted to `/data/local/tmp/llama/llama-b9620/` on the phone
- **Qwen 2.5 3B Instruct Q5_K_M GGUF** sits at `/sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf` (2.4 GB), read in place via the /sdcard FUSE mount — no copy into app storage needed
- **Mac wrapper script** at `./qwen.sh` (this directory)
- Verified perf: **~62 tok/s prompt eval, ~17 tok/s generation**, sub-second round trip once the model is page-cached

## How to use it

```bash
./qwen.sh "your prompt here"           # default 120 token cap
./qwen.sh -n 300 "longer answer"        # raise the cap
./qwen.sh -t 8 "use 8 threads"          # tweak threads
echo "multi line\nprompt" | ./qwen.sh   # stdin mode
```

What the wrapper does, in case you need to debug it:
1. Writes the prompt to `/sdcard/Download/prompt.txt` via `adb shell`
2. Runs `LD_LIBRARY_PATH=. ./llama-completion -m <gguf> -f <prompt> -n N -t T --no-display-prompt` on the phone
3. Strips the trailing `> EOF by user` line and prints

## Critical gotchas (read before changing anything)

1. **Use `llama-completion`, NOT `llama-cli`.** In build b9620, `llama-cli --no-conversation` prints `--no-conversation is not supported by llama-cli / please use llama-completion instead` and then silently enters chat REPL mode anyway. Since `adb shell` stdin never EOFs, it hangs forever printing empty `> ` prompts (we generated a 191 MB log of them before noticing).

2. **`LD_LIBRARY_PATH=.` is mandatory.** The CLI binaries (`llama-completion`, `llama-server`, etc.) are 7 KB thin wrappers; the actual code is in `libllama-*-impl.so` + `libllama.so` in the same directory. Without LD_LIBRARY_PATH the loader can't find them.

3. **Cold-load output buffering looks like a hang.** First inference after a phone reboot mmaps 2.4 GB from /sdcard FUSE — output through the adb pipe is block-buffered until enough accumulates. `ps -A | grep llama` showing state `R` with growing VmRSS = it's working. To monitor live, redirect to a phone-side file and tail it from Mac:
   ```bash
   adb shell "cd /data/local/tmp/llama/llama-b9620 && LD_LIBRARY_PATH=. ./llama-completion ... > /sdcard/Download/out.txt 2>&1 &"
   adb shell tail -f /sdcard/Download/out.txt
   ```

4. **`uiautomator dump` for tap coordinates.** If you ever need to drive an app UI via adb, don't guess tap coords from screenshots — the Read tool shows them scaled, not at native res. Use:
   ```bash
   adb shell "uiautomator dump /sdcard/ui.xml && cat /sdcard/ui.xml" | tr '>' '\n' | grep -iE "content-desc|text="
   ```
   Each node has `bounds="[x1,y1][x2,y2]"` in real screen coords. I was off by 2x guessing visually before this.

5. **Listening ports without `ss`/`netstat`.** Android shell doesn't have them. Read `/proc/net/tcp` and `/proc/net/tcp6`, filter on state column == `0A` (LISTEN). Local address is `IP:PORT` in big-endian hex (`00000000:1F90` = 0.0.0.0:8080, `0100007F:1F90` = 127.0.0.1:8080).

6. **`adb forward` empty-reply ≠ broken.** When `adb forward tcp:X tcp:X` is up but nothing's listening on the phone, curl returns `(52) Empty reply from server`, NOT connection-refused. Empty reply on a forwarded port = the on-phone server isn't running.

## Already explored — don't re-investigate

- **PocketPal AI v1.15.2 does NOT have a local HTTP server.** Settings end at "Export Options"; "API Settings" is just HF download token. `pm dump com.pocketpalai` shows zero custom services or intents — only standard Android system broadcasts. The only way to drive PocketPal from adb is UI input automation.
- **Termux + Ollama-from-source path was abandoned.** Setup script lingers at `/tmp/setup-ollama.sh` (Mac) and `/sdcard/Download/setup-ollama.sh` (phone). Safe to delete if you're tidying up.
- **PocketPal's GGUF files are in private app storage** (`/data/data/com.pocketpalai/...`), unreadable from adb shell without root. That's why we re-downloaded the GGUF instead of reusing PocketPal's.

## Device reference

- **ADB**: `/Users/archismanmidya/Library/Android/sdk/platform-tools/adb` (already on PATH)
- **Phone**: iQOO 15, serial `10BFBG0CEL001DB`, model `I2501`, arm64-v8a, Android 16, **1440 × 3168** display, 16 GB RAM, ~440 GB free on /sdcard
- **adb shell uid**: 2000(shell), in groups sdcard_rw / sdcard_r / inet — `/sdcard` readable, `/data/data/<app>/` not, `/data/local/tmp` writable+executable
- **Launcher activities** (resolve via `adb shell cmd package resolve-activity --brief <pkg>` — `.HomeActivity` shorthand does NOT work):
  - Termux: `com.termux/.app.TermuxActivity`
  - PocketPal AI: `com.pocketpalai/com.pocketpal.MainActivity`

## Possible next steps (if the user asks)

- **Expose as HTTP** for any OpenAI-compatible client (e.g. continue.dev, aider): the same tarball includes `llama-server` (backed by `libllama-server-impl.so`, 68 MB). Pattern:
  ```bash
  adb shell "cd /data/local/tmp/llama/llama-b9620 && LD_LIBRARY_PATH=. ./llama-server \
    -m /sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf --host 0.0.0.0 --port 8080 &"
  adb forward tcp:8080 tcp:8080
  curl http://localhost:8080/v1/chat/completions -d '{...}'
  ```
- **Try a bigger model** — phone has 16 GB RAM, ~440 GB free. Qwen 2.5 7B Q4_K_M (~4.5 GB) or Gemma 3 4B would fit comfortably. Just download a different GGUF to `/sdcard/Download/` and swap the path in `qwen.sh`.
- **Wake-lock during long inferences** — for prompts over a minute, `adb shell input keyevent KEYCODE_WAKEUP` or hold a wake-lock via `adb shell svc power stayon usb` to prevent thermal-throttling-from-sleep.
- **Multimodal** — `llama-llava-cli`, `llama-gemma3-cli`, `llama-mtmd-cli` are all in the same dir, ready to use with a vision-capable GGUF.

## Files of interest

| Path | Purpose |
|---|---|
| `./qwen.sh` | Mac wrapper — the user-facing CLI |
| `/data/local/tmp/llama/llama-b9620/` (phone) | llama.cpp binaries + .so libs |
| `/sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf` (phone) | The model |
| `/sdcard/Download/prompt.txt` (phone) | Scratch file for the current prompt — rewritten on every wrapper call |
| `~/.claude/projects/-Users-archismanmidya-Desktop-CrazyStuff-iqoo-mobile/memory/` | Memory notes from this session — same content as this doc but split by category |
