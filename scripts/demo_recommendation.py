"""End-to-end demo of the recommendation flow against the running services (standard library only).

Needs: backend on http://localhost:8080 and ML service on http://localhost:8001 (see README "Run both services").
Creates a new demo user with wheat, rice and maize fields, runs POST /api/fields/{id}/recommendations for each and
prints a summary. Usage:  python scripts/demo_recommendation.py [--base http://localhost:8080] [--json]

--seed-demo-user uses the fixed account demo@agrioptima.local / demo-pass-123 (for the React dashboard demo)
instead of a random one. If that account already has farms, nothing is created.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid
from datetime import date, timedelta


def call(base, method, path, token=None, body=None):
    req = urllib.request.Request(base + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json",
                                          **({"Authorization": f"Bearer {token}"} if token else {})})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")


def ok(result, expected=(200, 201)):
    status, body = result
    if status not in expected:
        sys.exit(f"HTTP {status}: {json.dumps(body, indent=2)}")
    return body


DEMO_EMAIL, DEMO_PASSWORD = "demo@agrioptima.local", "demo-pass-123"


def demo_user_token(base):
    """Logs in the fixed demo account, registering it on first use."""
    status, body = call(base, "POST", "/api/auth/login", body={"email": DEMO_EMAIL, "password": DEMO_PASSWORD})
    if status == 200:
        return body["accessToken"]
    return ok(call(base, "POST", "/api/auth/register",
                   body={"fullName": "Demo Farmer", "email": DEMO_EMAIL, "password": DEMO_PASSWORD}))["accessToken"]


def setup(base, token=None):
    token = token or ok(call(base, "POST", "/api/auth/register",
                             body={"fullName": "Demo Farmer", "email": f"demo-{uuid.uuid4().hex[:8]}@example.com",
                                   "password": "demo-pass-123"}))["accessToken"]
    crops = {c["code"]: c for c in ok(call(base, "GET", "/api/crops", token))}
    ferts = {f["code"]: f["id"] for f in ok(call(base, "GET", "/api/fertilizers", token))}

    def stage(crop, code):
        return next(s["id"] for s in ok(call(base, "GET", f"/api/crops/{crops[crop]['id']}/stages", token))
                    if s["code"] == code)

    farm = ok(call(base, "POST", "/api/farms", token, {"name": "Demo farm", "locationName": "Patna, Bihar",
                                                        "latitude": 25.594, "longitude": 85.137}))["id"]
    today = date.today()
    sown = date(today.year if today >= date(today.year, 11, 20) else today.year - 1, 11, 20)  # last rabi sowing
    fields = {}

    # 1. Wheat at CRI, 2 ha, medium soil, basal applied at sowing (docs/RECOMMENDATION_ENGINE.md example 1),
    #    using the most recent rabi season (sown 20 November)
    wheat = ok(call(base, "POST", f"/api/farms/{farm}/fields", token, {
        "name": "Wheat - CRI", "areaHa": 2.0, "soilType": "Sandy loam", "irrigationType": "IRRIGATED",
        "cropId": crops["WHEAT"]["id"], "growthStageId": stage("WHEAT", "CRI"), "season": "RABI",
        "sowingDate": sown.isoformat(), "previousCrop": "Rice"}))["id"]
    ok(call(base, "POST", f"/api/fields/{wheat}/soil-records", token, {
        "sampleDate": (sown - timedelta(days=10)).isoformat(), "nitrogen": 300, "phosphorus": 15, "potassium": 200,
        "ph": 7.2, "organicCarbon": 0.55}))
    for code, kg in (("DAP", 260.87), ("UREA", 71.83), ("MOP", 133.33)):
        ok(call(base, "POST", f"/api/fields/{wheat}/applications", token,
                {"fertilizerId": ferts[code], "appliedOn": sown.isoformat(), "quantityKg": kg}))
    fields["wheat"] = wheat

    # 2. Rice at panicle initiation, 0.5 ha, high-fertility soil (example 4)
    rice_sown = date.today() - timedelta(days=60)
    rice = ok(call(base, "POST", f"/api/farms/{farm}/fields", token, {
        "name": "Rice - PI", "areaHa": 0.5, "soilType": "Clay loam", "irrigationType": "IRRIGATED",
        "cropId": crops["RICE"]["id"], "growthStageId": stage("RICE", "PANICLE_INITIATION"), "season": "KHARIF",
        "sowingDate": rice_sown.isoformat(), "previousCrop": "Wheat"}))["id"]
    ok(call(base, "POST", f"/api/fields/{rice}/soil-records", token, {
        "sampleDate": (rice_sown - timedelta(days=5)).isoformat(), "nitrogen": 600, "phosphorus": 30,
        "potassium": 300, "ph": 6.8, "organicCarbon": 0.8}))
    fields["rice"] = rice

    # 3. Wheat at tillering, 1 ha, 400 kg urea already applied -> P + K only (example 6): the three plans differ
    pk = ok(call(base, "POST", f"/api/farms/{farm}/fields", token, {
        "name": "Wheat - P+K only", "areaHa": 1.0, "soilType": "Loam", "irrigationType": "IRRIGATED",
        "cropId": crops["WHEAT"]["id"], "growthStageId": stage("WHEAT", "TILLERING"), "season": "RABI",
        "sowingDate": sown.isoformat(), "previousCrop": "Rice"}))["id"]
    ok(call(base, "POST", f"/api/fields/{pk}/soil-records", token, {
        "sampleDate": (sown - timedelta(days=10)).isoformat(), "nitrogen": 300, "phosphorus": 15, "potassium": 200,
        "ph": 7.0, "organicCarbon": 0.6}))
    ok(call(base, "POST", f"/api/fields/{pk}/applications", token,
            {"fertilizerId": ferts["UREA"], "appliedOn": sown.isoformat(), "quantityKg": 400}))
    fields["wheat_pk_only"] = pk

    # 4. Maize (no training data for the yield model), rainfed, no soil test (example 5)
    maize = ok(call(base, "POST", f"/api/farms/{farm}/fields", token, {
        "name": "Maize - sowing", "areaHa": 1.2, "irrigationType": "RAINFED", "cropId": crops["MAIZE"]["id"],
        "growthStageId": stage("MAIZE", "SOWING"), "season": "KHARIF", "sowingDate": date.today().isoformat()}))["id"]
    fields["maize"] = maize
    return token, fields


def summary(name, rec):
    req = rec["requirement"]["dueNowKgHa"]
    print(f"\n=== {name}: {rec['crop']['code']} / {rec['growthStage']['code']} / {rec['field']['areaHa']} ha "
          f"-> {rec['status']} (feasible={rec['feasible']}, id={rec['id']})")
    print(f"requirement due now (kg/ha): N {req['n']}  P2O5 {req['p2o5']}  K2O {req['k2o']}")
    print(f"scoring: {rec.get('scoring', {}).get('mode')}   model: {rec.get('modelVersion')}")
    for p in rec["plans"]:
        items = ", ".join(f"{i['code']} {i['kgHa']}" for i in p["items"]) or "nothing"
        y = p["yield"]["predictedYieldTHa"] if p["yield"]["available"] else "n/a"
        print(f"  {'*' if p['selected'] else ' '} {p['strategy']:<12} {items:<48} cost {p['costPerHa']:>9} INR/ha  "
              f"excess {p['totalExcessKgHa']:>7} kg/ha  yield {y} t/ha  score {p['score']['scorePerHa']}")
    if rec.get("selectedPlan"):
        print(f"selected: {rec['selectedPlan']['strategy']} - {rec['selectedPlan']['reason']}")
    if not rec["feasible"]:
        print(f"infeasible: {rec['infeasibilityReason']}")
    for w in rec["warnings"]:
        print(f"  warning: {w}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--json", metavar="NAME", help="also print the full response JSON of one case "
                    "(wheat, rice, wheat_pk_only, maize)")
    ap.add_argument("--seed-demo-user", action="store_true",
                    help=f"use the fixed account {DEMO_EMAIL} / {DEMO_PASSWORD}; skip if it already has farms")
    args = ap.parse_args()
    demo_token = None
    if args.seed_demo_user:
        demo_token = demo_user_token(args.base)
        if ok(call(args.base, "GET", "/api/farms", demo_token)):
            print(f"{DEMO_EMAIL} already has demo data; nothing created. Sign in with password {DEMO_PASSWORD}.")
            return
    token, fields = setup(args.base, demo_token)
    for name, field_id in fields.items():
        rec = ok(call(args.base, "POST", f"/api/fields/{field_id}/recommendations", token))
        summary(name, rec)
        if args.json == name:
            print(json.dumps(rec, indent=2, ensure_ascii=False))
    history = ok(call(args.base, "GET", f"/api/fields/{fields['wheat']}/recommendations", token))
    print(f"\nstored recommendations for the wheat field: {len(history)}")


if __name__ == "__main__":
    main()
