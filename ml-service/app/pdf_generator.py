"""PDF Prescription Generator for AgriOptima Recommendations.

Generates a professional 1-page A4 soil health & fertilizer prescription
containing field summary, soil test values, optimized fertilizer application
schedule with bag counts and costs, weather risk advisory, and IPNS organic blending.
"""

from __future__ import annotations

import io
from typing import Any
from fpdf import FPDF


def sanitize_text(text: str | None) -> str:
    """Replaces Unicode characters with Latin-1 equivalents for standard PDF fonts."""
    if not text:
        return ""
    replacements = {
        "\u20b9": "Rs. ",
        "\u2013": "-",
        "\u2014": "--",
        "\u2018": "'",
        "\u2019": "'",
        "\u201c": '"',
        "\u201d": '"',
        "\u2022": "*",
        "\u2082": "2",
        "\u2085": "5",
        "\u00b2": "2",
        "\u00b0": " deg ",
        "\u2265": ">=",
        "\u2264": "<=",
        "\u2192": "->",
    }
    for orig, rep in replacements.items():
        text = text.replace(orig, rep)
    return text.encode("latin-1", "replace").decode("latin-1")


class PrescriptionPDF(FPDF):
    def header(self):
        # Green header bar
        self.set_fill_color(34, 139, 34)  # Forest Green
        self.rect(0, 0, 210, 24, "F")
        self.set_text_color(255, 255, 255)
        self.set_font("Helvetica", "B", 15)
        self.set_xy(10, 4)
        self.cell(190, 8, "AGRIOPTIMA SOIL & FERTILIZER PRESCRIPTION", align="C", new_x="LMARGIN", new_y="NEXT")
        self.set_font("Helvetica", "I", 9)
        self.cell(190, 6, "AI/ML Category PSAI01: Sustainable Fertilizer Usage Optimizer for Higher Yield", align="C", new_x="LMARGIN", new_y="NEXT")
        self.ln(8)

    def footer(self):
        self.set_y(-14)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(130, 130, 130)
        self.cell(0, 5, "Prototype Prescription | Consult local Krishi Vigyan Kendra (KVK) agronomists before application.", align="C", new_x="LMARGIN", new_y="NEXT")
        self.cell(0, 4, f"Page {self.page_no()}", align="C")


def create_prescription_pdf(rec: dict[str, Any]) -> bytes:
    """Builds and returns the PDF bytes for a given RecommendationResponse dictionary."""
    pdf = PrescriptionPDF(orientation="P", unit="mm", format="A4")
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.add_page()

    field = rec.get("field", {})
    crop = rec.get("crop", {})
    stage = rec.get("growthStage", {})
    soil = rec.get("soil", {})
    plans = rec.get("plans", [])
    selected_meta = rec.get("selectedPlan", {})
    weather = rec.get("weather", {})
    ipns = rec.get("ipns", {})
    micronutrients = rec.get("micronutrients", [])

    # Find the selected plan
    selected_plan = None
    for p in plans:
        if p.get("selected") or p.get("strategy") == selected_meta.get("strategy"):
            selected_plan = p
            break
    if not selected_plan and plans:
        selected_plan = plans[0]

    # --- SECTION 1: FIELD & CROP PROFILE ---
    pdf.set_font("Helvetica", "B", 10)
    pdf.set_text_color(34, 100, 34)
    pdf.cell(0, 6, "1. FIELD & CROP PROFILE", new_x="LMARGIN", new_y="NEXT")
    pdf.line(10, pdf.get_y(), 200, pdf.get_y())
    pdf.ln(2)

    pdf.set_font("Helvetica", "", 8.5)
    pdf.set_text_color(40, 40, 40)
    farm_name = sanitize_text(field.get("farmName", "Demo Farm"))
    field_name = sanitize_text(field.get("name", "Field"))
    location = sanitize_text(field.get("location", "Bihar, India"))
    crop_name = sanitize_text(crop.get("name", "Wheat"))
    stage_name = sanitize_text(stage.get("name", "CRI"))
    area_ha = field.get("areaHa", 1.0)
    sowing = sanitize_text(field.get("sowingDate") or "Not recorded")

    col_w = 47.5
    pdf.cell(col_w, 5, f"Farm: {farm_name}")
    pdf.cell(col_w, 5, f"Field: {field_name}")
    pdf.cell(col_w, 5, f"Area: {area_ha:.2f} ha")
    pdf.cell(col_w, 5, f"Location: {location}", new_x="LMARGIN", new_y="NEXT")

    pdf.cell(col_w, 5, f"Crop: {crop_name}")
    pdf.cell(col_w, 5, f"Growth Stage: {stage_name}")
    pdf.cell(col_w, 5, f"Sowing Date: {sowing}")
    strat_label = sanitize_text(selected_meta.get("label", "Optimal"))
    pdf.cell(col_w, 5, f"Strategy: {strat_label}", new_x="LMARGIN", new_y="NEXT")
    pdf.ln(3)

    # --- SECTION 2: SOIL HEALTH STATUS ---
    pdf.set_font("Helvetica", "B", 10)
    pdf.set_text_color(34, 100, 34)
    pdf.cell(0, 6, "2. SOIL HEALTH DIAGNOSTIC", new_x="LMARGIN", new_y="NEXT")
    pdf.line(10, pdf.get_y(), 200, pdf.get_y())
    pdf.ln(2)

    pdf.set_font("Helvetica", "", 8.5)
    pdf.set_text_color(40, 40, 40)
    n_val = soil.get("availableNKgHa", 0)
    p_val = soil.get("availablePKgHa", 0)
    k_val = soil.get("availableKKgHa", 0)
    ph_val = soil.get("ph", 7.0)
    oc_val = soil.get("organicCarbonPct")
    oc_str = f"{oc_val:.2f}%" if oc_val is not None else "Not recorded"

    pdf.cell(col_w, 5, f"Available N: {n_val:.1f} kg/ha ({sanitize_text(soil.get('nClass', 'Medium'))})")
    pdf.cell(col_w, 5, f"Available P: {p_val:.1f} kg/ha ({sanitize_text(soil.get('pClass', 'Medium'))})")
    pdf.cell(col_w, 5, f"Available K: {k_val:.1f} kg/ha ({sanitize_text(soil.get('kClass', 'Medium'))})")
    pdf.cell(col_w, 5, f"pH: {ph_val:.1f} | OC: {oc_str}", new_x="LMARGIN", new_y="NEXT")

    if micronutrients:
        micro_str = " | ".join(
            f"{m.get('name')}: {m.get('value')}{m.get('unit')} ({m.get('status')})"
            for m in micronutrients[:4]
        )
        pdf.set_font("Helvetica", "I", 8)
        pdf.cell(0, 4.5, sanitize_text(f"Micronutrients: {micro_str}"), new_x="LMARGIN", new_y="NEXT")

    pdf.ln(3)

    # --- SECTION 3: RECOMMENDED FERTILIZER APPLICATION ---
    pdf.set_font("Helvetica", "B", 10)
    pdf.set_text_color(34, 100, 34)
    pdf.cell(0, 6, "3. RECOMMENDED FERTILIZER SCHEDULE (OPTIMAL PLAN)", new_x="LMARGIN", new_y="NEXT")
    pdf.line(10, pdf.get_y(), 200, pdf.get_y())
    pdf.ln(2)

    # Table Header
    pdf.set_fill_color(240, 248, 240)
    pdf.set_font("Helvetica", "B", 8)
    pdf.set_text_color(20, 20, 20)
    pdf.cell(40, 6, "Fertilizer Product", border=1, fill=True)
    pdf.cell(25, 6, "Rate (kg/ha)", border=1, align="R", fill=True)
    pdf.cell(25, 6, "50kg Bags/ha", border=1, align="R", fill=True)
    pdf.cell(30, 6, "Total Field (kg)", border=1, align="R", fill=True)
    pdf.cell(35, 6, "Cost/ha (Rs.)", border=1, align="R", fill=True)
    pdf.cell(35, 6, "Field Cost (Rs.)", border=1, align="R", fill=True, new_x="LMARGIN", new_y="NEXT")

    pdf.set_font("Helvetica", "", 8)
    items = selected_plan.get("items", []) if selected_plan else []
    for item in items:
        p_name = sanitize_text(item.get("name", item.get("code", "Fertilizer")))
        kg_ha = item.get("kgHa", 0.0)
        bag_kg = item.get("bagKg") or 50.0
        bags_ha = kg_ha / bag_kg
        field_kg = item.get("fieldKg", 0.0)
        cost_ha = item.get("costPerHa", 0.0)
        field_cost = item.get("fieldCost", 0.0)

        pdf.cell(40, 5.5, p_name[:22], border=1)
        pdf.cell(25, 5.5, f"{kg_ha:.1f}", border=1, align="R")
        pdf.cell(25, 5.5, f"{bags_ha:.1f}", border=1, align="R")
        pdf.cell(30, 5.5, f"{field_kg:.1f}", border=1, align="R")
        pdf.cell(35, 5.5, f"{cost_ha:,.2f}", border=1, align="R")
        pdf.cell(35, 5.5, f"{field_cost:,.2f}", border=1, align="R", new_x="LMARGIN", new_y="NEXT")

    # Plan Summary Row
    if selected_plan:
        tot_cost_ha = selected_plan.get("costPerHa", 0.0)
        tot_field_cost = selected_plan.get("fieldCost", 0.0)
        pyield = selected_plan.get("yield", {})
        yield_str = f"{pyield.get('predictedYieldTHa', 0.0):.2f} t/ha" if pyield.get("available") else "N/A"

        pdf.set_font("Helvetica", "B", 8)
        pdf.set_fill_color(245, 245, 245)
        pdf.cell(90, 6, f"TOTAL ESTIMATED FERTILIZER COST (Yield Forecast: {yield_str})", border=1, fill=True)
        pdf.cell(30, 6, "", border=1, fill=True)
        pdf.cell(35, 6, f"Rs. {tot_cost_ha:,.2f}", border=1, align="R", fill=True)
        pdf.cell(35, 6, f"Rs. {tot_field_cost:,.2f}", border=1, align="R", fill=True, new_x="LMARGIN", new_y="NEXT")

    pdf.ln(3)

    # --- SECTION 4: WEATHER & IPNS ADVISORIES ---
    pdf.set_font("Helvetica", "B", 10)
    pdf.set_text_color(34, 100, 34)
    pdf.cell(0, 6, "4. SUSTAINABILITY & AGRO-MET ADVISORY", new_x="LMARGIN", new_y="NEXT")
    pdf.line(10, pdf.get_y(), 200, pdf.get_y())
    pdf.ln(2)

    # Weather Risk
    if weather:
        w_warn = weather.get("heavy_rain_warning", False)
        temp = weather.get("temperature", 25.0)
        rain = weather.get("rainfall_7d_mm", 0.0)
        pdf.set_font("Helvetica", "B", 8.5)
        if w_warn:
            pdf.set_text_color(180, 50, 0)
            pdf.cell(0, 4.5, sanitize_text(f"[!] WEATHER HAZARD: Heavy Rain Expected ({rain:.1f} mm in 7 days, 3-day max temp {temp:.1f}C)."), new_x="LMARGIN", new_y="NEXT")
            pdf.set_font("Helvetica", "", 8)
            pdf.cell(0, 4, sanitize_text("    Advisory: Delay surface application of Urea until heavy precipitation ceases to prevent leaching."), new_x="LMARGIN", new_y="NEXT")
        else:
            pdf.set_text_color(50, 100, 50)
            pdf.cell(0, 4.5, sanitize_text(f"[OK] Weather Window: Favorable conditions ({temp:.1f}C, {rain:.1f} mm rain expected). Normal application safe."), new_x="LMARGIN", new_y="NEXT")

    # IPNS Advisory
    if ipns:
        pdf.set_font("Helvetica", "B", 8.5)
        pdf.set_text_color(20, 100, 40)
        pdf.cell(0, 4.5, sanitize_text("[+] IPNS ORGANIC BLENDING ADVISORY (25% Chemical Nitrogen Substitution):"), new_x="LMARGIN", new_y="NEXT")
        pdf.set_font("Helvetica", "", 8)
        pdf.set_text_color(50, 50, 50)
        chem_n = ipns.get("chemicalNKgHa", 0.0)
        fym_ha = ipns.get("fymKgHa", 0.0)
        vermi_ha = ipns.get("vermicompostKgHa", 0.0)
        pdf.multi_cell(190, 4, sanitize_text(
            f"Apply {chem_n:.1f} kg/ha synthetic N alongside {fym_ha:,.0f} kg/ha Farm Yard Manure (FYM) "
            f"or {vermi_ha:,.0f} kg/ha Vermicompost. This restores Soil Organic Carbon and lowers cash expenditure."
        ))

    pdf.ln(2)

    return bytes(pdf.output())
