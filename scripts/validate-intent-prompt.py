#!/usr/bin/env python3
# Validate the intent-router prompt against the live llama-server.
# Setup:  ./scripts/start-llama-server.sh  &&  adb forward tcp:8080 tcp:8080
# Run:    python3 scripts/validate-intent-prompt.py
# IMPORTANT: keep SYSTEM_PROMPT below in sync with IntentRouter.INTENT_SYSTEM_PROMPT.
import json, urllib.request

URL = "http://localhost:8080/v1/chat/completions"

SYSTEM_PROMPT = """You are a router for a kirana shop assistant. Read the shopkeeper's message (Hindi/Hinglish) and output ONLY which action it wants, as JSON.
Return ONLY: {"intent": one of "log_sale" | "record_payment" | "query_pnl" | "query_top_sellers" | "query_customer" | "query_day_trend" | "unknown"}
No explanation. No markdown. Just the raw JSON object.

Meaning:
- log_sale = recording a sale/purchase of goods (items + quantity, cash or उधार/credit). e.g. selling rice, sugar, soap.
- record_payment = a customer PAID BACK money they owed (दिए / चुकाए / चुका दिया / जमा / paid). No goods involved.
- query_pnl = asking about TOTAL earnings/profit/sales over a period (कमाई, मुनाफा, बिक्री, कितना कमाया; today/week/month).
- query_top_sellers = asking WHICH ITEMS sell the most (सबसे ज्यादा क्या बिका, बेस्ट सेलर, टॉप आइटम, कौन सा सामान ज्यादा बिकता है). A per-item ranking, NOT a total.
- query_customer = asking about ONE customer's account: how much they owe, their total, their history (रमेश का हिसाब, प्रिया कितना बकाया है, सुरेश ने कुल कितना लिया). Names a person.
- query_day_trend = asking WHICH DAY/weekday is busiest or sells most (कौन सा दिन सबसे busy, किस दिन सबसे ज्यादा बिक्री, सबसे अच्छा दिन).
- unknown = anything else.

Examples:
Input: दो किलो चावल अस्सी रुपये उधार रमेश को
{"intent":"log_sale"}
Input: तीन साबुन बीस बीस के
{"intent":"log_sale"}
Input: रमेश ने पचास रुपये दिए
{"intent":"record_payment"}
Input: प्रिया ने अपना उधार चुका दिया सौ रुपये
{"intent":"record_payment"}
Input: आज की कमाई कितनी हुई
{"intent":"query_pnl"}
Input: इस हफ्ते का मुनाफा बताओ
{"intent":"query_pnl"}
Input: सबसे ज्यादा क्या बिका
{"intent":"query_top_sellers"}
Input: इस महीने के टॉप आइटम कौन से हैं
{"intent":"query_top_sellers"}
Input: रमेश का हिसाब बताओ
{"intent":"query_customer"}
Input: प्रिया कितना बकाया है
{"intent":"query_customer"}
Input: कौन सा दिन सबसे busy रहता है
{"intent":"query_day_trend"}
Input: किस दिन सबसे ज्यादा बिक्री होती है
{"intent":"query_day_trend"}
Input: नमस्ते
{"intent":"unknown"}"""

RESPONSE_FORMAT = {
    "type": "json_schema",
    "json_schema": {
        "name": "intent",
        "schema": {
            "type": "object",
            "properties": {"intent": {"enum": ["log_sale", "record_payment", "query_pnl", "query_top_sellers", "query_customer", "query_day_trend", "unknown"]}},
            "required": ["intent"],
        },
    },
}

# (input, expected_intent)
CASES = [
    ("दो किलो चावल अस्सी रुपये उधार रमेश को", "log_sale"),
    ("teen soap bees-bees ke", "log_sale"),
    ("पाँच किलो चीनी दो सौ रुपये", "log_sale"),
    ("रमेश ने पचास रुपये दिए", "record_payment"),
    ("प्रिया ने अपना उधार चुका दिया", "record_payment"),
    ("सुरेश ने दो सौ जमा किए", "record_payment"),
    ("आज की कमाई कितनी हुई", "query_pnl"),
    ("इस हफ्ते का मुनाफा बताओ", "query_pnl"),
    ("इस महीने की बिक्री", "query_pnl"),
    ("सबसे ज्यादा क्या बिका", "query_top_sellers"),
    ("इस महीने के टॉप आइटम", "query_top_sellers"),
    ("रमेश का हिसाब", "query_customer"),
    ("प्रिया कितना बकाया है", "query_customer"),
    ("कौन सा दिन सबसे busy रहता है", "query_day_trend"),
    ("किस दिन सबसे ज्यादा बिक्री", "query_day_trend"),
    ("नमस्ते कैसे हो", "unknown"),
]

def extract_json(raw):
    s = raw.strip()
    for p in ("```json", "```"):
        if s.startswith(p): s = s[len(p):]
    s = s.rstrip("`").strip()
    a, b = s.find("{"), s.rfind("}")
    return s[a:b+1] if a != -1 and b > a else None

def call(text):
    body = json.dumps({
        "messages": [{"role": "system", "content": SYSTEM_PROMPT},
                     {"role": "user", "content": text}],
        "temperature": 0.1, "max_tokens": 64, "response_format": RESPONSE_FORMAT,
    }).encode()
    req = urllib.request.Request(URL, body, {"Content-Type": "application/json"})
    resp = json.loads(urllib.request.urlopen(req, timeout=60).read())
    return resp["choices"][0]["message"]["content"]

passed = 0
print(f"{'input':<42} {'got':<16} {'exp':<16} OK")
print("-" * 80)
for text, exp in CASES:
    try:
        js = extract_json(call(text))
        got = (json.loads(js).get("intent") if js else "?")
        ok = got == exp
        passed += ok
        print(f"{text[:40]:<42} {str(got):<16} {exp:<16} {'PASS' if ok else 'FAIL'}")
    except Exception as ex:
        print(f"{text[:40]:<42} ERROR: {ex}")
print("-" * 80)
print(f"RESULT: {passed}/{len(CASES)} passed")
