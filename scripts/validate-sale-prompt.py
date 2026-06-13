#!/usr/bin/env python3
# Validate the sale-parser prompt against the live llama-server on the §18 test cases.
# Setup:  ./scripts/start-llama-server.sh  &&  adb forward tcp:8080 tcp:8080
# Run:    python3 scripts/validate-sale-prompt.py
# IMPORTANT: keep SYSTEM_PROMPT below in sync with LlmEngine.SALE_SYSTEM_PROMPT.
import json, urllib.request, sys

URL = "http://localhost:8080/v1/chat/completions"

# ---- The prompt under test (must mirror LlmEngine.SALE_SYSTEM_PROMPT) ----
SYSTEM_PROMPT = """You are a kirana store billing assistant. Parse the input into JSON only.
Return ONLY the JSON object. No explanation. No markdown. No preamble. Just the raw JSON object.

Schema:
{"entries":[{"item":string|null,"qty":string|null,"amount":number|null,"type":"cash"|"credit"|"repayment","party":string|null}]}

Field meaning:
- item = the product name only (e.g. "चावल"). Never include a price or number in item.
- qty = amount of product WITH its unit (e.g. "2 किलो", "3"). Never put the rupee price here.
- amount = the total rupee PRICE as a plain number in digits.
- party = person's name only. Strip "को/ko/ने/ne/से/se".

Convert Hindi number words to digits:
एक=1 दो=2 तीन=3 चार=4 पाँच=5 छह=6 सात=7 आठ=8 नौ=9 दस=10 ग्यारह=11 बारह=12
बीस=20 तीस=30 चालीस=40 पचास=50 साठ=60 सत्तर=70 अस्सी=80 नब्बे=90 सौ=100 हज़ार=1000
पैंतालीस=45 पचपन=55 पैंसठ=65 पचहत्तर=75 पचासी=85 पंचानवे=95
Compounds add up: "दो सौ"=200, "दो सौ पचास"=250, "साढ़े तीन सौ"=350.
Fractions: डेढ़=1.5 ढाई=2.5 सवा=1.25 पौने=0.75 आधा=0.5.

Rules:
- type defaults to "cash". "उधार"/"udhaar" = "credit". "दिए"/"ne diya" = "repayment".
- "X X के" (e.g. "बीस बीस के") = each unit costs X; set amount = qty × X.
- ONE product = ONE entry. Do not split one product into multiple entries.
- Return ONLY valid JSON.

Examples:
Input: दो किलो चावल अस्सी रुपये
{"entries":[{"item":"चावल","qty":"2 किलो","amount":80,"type":"cash","party":null}]}
Input: रमेश को दस किलो आटा तीन सौ का उधार
{"entries":[{"item":"आटा","qty":"10 किलो","amount":300,"type":"credit","party":"रमेश"}]}
Input: तीन साबुन बीस बीस के
{"entries":[{"item":"साबुन","qty":"3","amount":60,"type":"cash","party":null}]}
Input: रमेश ने पचास रुपये दिए
{"entries":[{"item":null,"qty":null,"amount":50,"type":"repayment","party":"रमेश"}]}
Input: ढाई किलो दाल नब्बे रुपये
{"entries":[{"item":"दाल","qty":"2.5 किलो","amount":90,"type":"cash","party":null}]}"""

# ---- test cases: (input, expected_type, expected_party, expected_amount) ----
# Mix of §18 + spoken-Hindi (Devanagari number words) that broke pre-hardening.
CASES = [
    ("दो किलो चावल, अस्सी रुपये, उधार रमेश को", "credit", "Ramesh", 80),
    ("2 kilo cheeni forty rupees", "cash", None, 40),
    ("रमेश ने पचास रुपये दिए", "repayment", "Ramesh", 50),
    ("teen soap bees-bees ke credit Priya", "credit", "Priya", 60),
    ("पाँच किलो चीनी दो सौ रुपये", "cash", None, 200),
    ("ढाई किलो दाल नब्बे रुपये", "cash", None, 90),
    ("एक किलो चीनी पैंतालीस रुपये उधार प्रिया को", "credit", "Priya", 45),
    ("दो सौ पचास रुपये का तेल", "cash", None, 250),
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
print(f"{'input':<40} {'type':<10} {'amt':<8} {'party':<8} {'OK'}")
print("-" * 80)
for text, et, ep, ea in CASES:
    try:
        content = call(text)
        js = extract_json(content)
        obj = json.loads(js) if js else {}
        e0 = (obj.get("entries") or [{}])[0]
        gt, gp = clean(e0.get("type")) or "cash", clean(e0.get("party"))
        ga = e0.get("amount")
        ok = (gt == et) and party_ok(ep, gp) and (ga == ea)
        passed += ok
        print(f"{text[:38]:<40} {gt:<10} {str(ga):<8} {str(gp):<8} {'PASS' if ok else 'FAIL (exp t=%s amt=%s)' % (et, ea)}")
    except Exception as ex:
        print(f"{text[:38]:<40} ERROR: {ex}")
print("-" * 80)
print(f"RESULT: {passed}/{len(CASES)} passed")
