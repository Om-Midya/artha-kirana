# Artha Kirana — Demo Runbook

Operational checklist for running the app + on-device LLM on the iQOO 15.

## Step 0 — Start the on-device LLM (REQUIRED before any sale parsing)

The app reaches the model over HTTP on `127.0.0.1:8080`. `llama-server` must be running on the phone:

```bash
./scripts/start-llama-server.sh        # waits for /health, prints when UP
# stop:  adb shell "pkill -f llama-server"
# logs:  adb shell tail -f /sdcard/Download/llama-server.log
```

If the server is down, the app shows "LLM offline — start the server" and every parse falls
back to the manual-entry form (it never crashes).

## Step 1 — Battery / background (OriginOS) — SPIKE C

Re-run before every demo session (resets on restart):

```bash
adb shell device_config put activity_manager max_phantom_processes 2147483647
adb shell dumpsys deviceidle whitelist +com.artha.kirana
adb shell cmd appops set com.artha.kirana RUN_IN_BACKGROUND allow
adb shell cmd appops set com.artha.kirana RUN_ANY_IN_BACKGROUND allow
# verify:
adb shell dumpsys deviceidle whitelist | grep com.artha.kirana
```

Manual fallback on device: Settings → Apps → Artha Kirana → Battery → Unrestricted; Auto-start → ON.

## Spike results (record here)

- **SPIKE A — LLM connectivity:** ✅ PASS (2026-06-13). `POST /v1/chat/completions` on `127.0.0.1:8080`
  returned valid JSON for "दो किलो चावल अस्सी रुपये उधार रमेश" → `type=credit, party=रमेश` correct.
  6.4s for a 146-token-system-prompt parse (prompt 32 tok/s, gen 20.8 tok/s). Response shape
  `choices[0].message.content` matches the planned DTO. (amount extraction needs prompt tuning — Task 1.7.)
- **SPIKE B — Hindi offline STT:** ⏳ PENDING USER — needs speaking "दो किलो चावल" in airplane mode.
  Run before Phase 4. If it fails, set `VOICE_ENABLED=false` and typed entry covers the demo.
- **SPIKE C — battery whitelist:** ✅ PASS (2026-06-13). `user,com.artha.kirana,10346` in deviceidle whitelist.

## §18 LLM test-case results (Phase 1 validation)

| Input | Expected type | Expected party | Got | Pass |
|---|---|---|---|---|
| दो किलो चावल, अस्सी रुपये, उधार रमेश को | credit | Ramesh | | |
| 2 kilo cheeni forty rupees | cash | null | | |
| रमेश ने पचास रुपये दिए | repayment | Ramesh | | |
| teen soap bees-bees ke credit Priya | credit | Priya | | |
| chawal aur daal kul 120 | cash | null | | |

## 2-minute demo script (CLAUDE-1.md §15 Phase 6)

1. Airplane mode ON (show judges).
2. Type/speak a sale in Hindi → parsed → ledger.
3. Credit entry for Ramesh → khata updates.
4. Snap a bill → items confirmed → inventory up.
5. P&L tab → "₹X gross profit today".
6. Low-stock notification fires.
7. Network ON → Insights tab → 3 Claude insights.
8. Close: "Everything except the last screen ran on the iQOO. Nothing left this phone."
