#!/usr/bin/env python3
# Validate the sale-parser prompt against the live llama-server on the §18 test cases.
# Setup:  ./scripts/start-llama-server.sh  &&  adb forward tcp:8080 tcp:8080
# Run:    python3 scripts/validate-sale-prompt.py
# IMPORTANT: keep SYSTEM_PROMPT below in sync with LlmEngine.SALE_SYSTEM_PROMPT.
import json, urllib.request, sys

URL = "http://localhost:8080/v1/chat/completions"

# ---- The prompt under test (must mirror LlmEngine.SALE_SYSTEM_PROMPT) ----
SYSTEM_PROMPT = """You are a kirana store billing assistant. Parse the input into JSON only.
Return ONLY the JSON object. No explanation. No markdown. No preamble.
No "here is the JSON". Just the raw JSON object.

Schema:
{"entries":[{"item":string|null,"qty":string|null,"amount":number|null,"type":"cash"|"credit"|"repayment","party":string|null}]}

Rules:
- type defaults to "cash" if not mentioned
- "उधार" or "udhaar" means type = "credit"
- "दिए" or "ne diya" means type = "repayment"
- party is ONLY the person's name. Strip postpositions like "को"/"ko"/"ने"/"ne"/"से"/"se" (e.g. "रमेश को" -> "रमेश", "Ramesh ko" -> "Ramesh")
- Multiple items in one sentence = multiple entries in the array
- Return ONLY valid JSON. Nothing else."""

# ---- §18 test cases: (input, expected_type, expected_party) ----
CASES = [
    ("दो किलो चावल, अस्सी रुपये, उधार रमेश को", "credit", "Ramesh"),
    ("2 kilo cheeni forty rupees", "cash", None),
    ("रमेश ने पचास रुपये दिए", "repayment", "Ramesh"),
    ("teen soap bees-bees ke credit Priya", "credit", "Priya"),
    ("chawal aur daal kul 120", "cash", None),
]

DEV = {"Ramesh": ["रमेश"], "Priya": ["प्रिया", "प्रीया"]}

def extract_json(raw):
    s = raw.strip()
    for p in ("```json", "```"):
        if s.startswith(p): s = s[len(p):]
    s = s.rstrip("`").strip()
    a, b = s.find("{"), s.rfind("}")
    return s[a:b+1] if a != -1 and b > a else None

def clean(v):
    if v is None: return None
    v = str(v).strip()
    return None if v == "" or v.lower() in ("null", "none") else v

def call(text):
    body = json.dumps({
        "messages": [{"role": "system", "content": SYSTEM_PROMPT},
                     {"role": "user", "content": text}],
        "temperature": 0.1, "max_tokens": 256, "stop": ["```"],
    }).encode()
    req = urllib.request.Request(URL, body, {"Content-Type": "application/json"})
    resp = json.loads(urllib.request.urlopen(req, timeout=60).read())
    return resp["choices"][0]["message"]["content"]

def party_ok(exp, got):
    if exp is None: return got is None
    if got is None: return False
    return got.lower() == exp.lower() or got in DEV.get(exp, [])

passed = 0
print(f"{'input':<42} {'exp':<10} {'got-type':<10} {'party':<10} {'OK'}")
print("-" * 88)
for text, et, ep in CASES:
    try:
        content = call(text)
        js = extract_json(content)
        obj = json.loads(js) if js else {}
        e0 = (obj.get("entries") or [{}])[0]
        gt, gp = clean(e0.get("type")) or "cash", clean(e0.get("party"))
        ok = (gt == et) and party_ok(ep, gp)
        passed += ok
        print(f"{text[:40]:<42} {et:<10} {gt:<10} {str(gp):<10} {'PASS' if ok else 'FAIL'}")
    except Exception as ex:
        print(f"{text[:40]:<42} {et:<10} ERROR: {ex}")
print("-" * 88)
print(f"RESULT: {passed}/{len(CASES)} passed")
