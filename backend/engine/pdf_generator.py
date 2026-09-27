"""
PDF Prescription Generator
Author: Amey Barbade

Generates a clean, 1-page PDF soil prescription and fertilizer schedule
in-memory using fpdf2.
"""
import io
from fpdf import FPDF


def sanitize_text(text: str) -> str:
    """Sanitizes unicode characters for core latin-1 PDF fonts."""
    if not isinstance(text, str):
        text = str(text)
    replacements = {
        "—": " - ",
        "–": "-",
        "₹": "Rs. ",
        "’": "'",
        "‘": "'",
        "“": '"',
        "”": '"',
        "₂": "2",
        "₅": "5",
        "•": "*",
        "…": "...",
        "°": " deg ",
    }
    for k, v in replacements.items():
        text = text.replace(k, v)
    return text.encode("latin-1", "replace").decode("latin-1")


class AgritechPDF(FPDF):
    def header(self):
        # Deep green banner
        self.set_fill_color(74, 124, 89)
        self.rect(0, 0, 210, 22, "F")
        self.set_xy(10, 3)
        self.set_font("Helvetica", "B", 14)
        self.set_text_color(255, 255, 255)
        self.cell(0, 8, sanitize_text("Agritech Soil Prescription & Schedule"), align="C", new_x="LMARGIN", new_y="NEXT")
        self.set_font("Helvetica", "", 9)
        self.cell(0, 5, sanitize_text("SmartAgri PSAI01 - Precision Nutrient Management Plan"), align="C", new_x="LMARGIN", new_y="NEXT")
        self.set_y(26)

    def footer(self):
        self.set_y(-12)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(120, 120, 120)
        self.cell(0, 8, sanitize_text("Generated via PSAI01 Hybrid STCR Engine | Author: Amey Barbade"), align="C")


def create_prescription_pdf(
    crop_type: str,
    target_yield: float,
    land_size_acres: float,
    soil_data: dict,
    commercial_bags: dict,
    schedule: list,
    cost_comparison: dict,
    confidence: str,
    location: dict = None
) -> bytes:
    """
    Creates a clean, structured 1-page PDF prescription in-memory.
    Returns raw PDF bytes.
    """
    pdf = AgritechPDF(orientation="P", unit="mm", format="A4")
    pdf.set_auto_page_break(auto=False)
    pdf.add_page()

    # ── Section 1: Farm & Parcel Details ──
    pdf.set_fill_color(243, 239, 230)  # #F3EFE6
    pdf.set_draw_color(221, 216, 204)  # #DDD8CC
    pdf.rect(10, 26, 190, 22, "FD")

    pdf.set_xy(14, 28)
    pdf.set_font("Helvetica", "B", 10)
    pdf.set_text_color(45, 45, 45)
    pdf.cell(45, 6, "Crop Type:", 0)
    pdf.set_font("Helvetica", "", 10)
    pdf.cell(45, 6, sanitize_text(str(crop_type).capitalize()), 0)

    pdf.set_font("Helvetica", "B", 10)
    pdf.cell(45, 6, "GIS-Detected Area:", 0)
    pdf.set_font("Helvetica", "", 10)
    pdf.cell(45, 6, f"{land_size_acres:.2f} Acres", 0, new_x="LMARGIN", new_y="NEXT")

    pdf.set_x(14)
    pdf.set_font("Helvetica", "B", 10)
    pdf.cell(45, 6, "Target Yield:", 0)
    pdf.set_font("Helvetica", "", 10)
    pdf.cell(45, 6, f"{target_yield} q/ha", 0)

    pdf.set_font("Helvetica", "B", 10)
    pdf.cell(45, 6, "Recommendation Mode:", 0)
    pdf.set_font("Helvetica", "B", 10)
    if confidence == "stcr_grounded":
        pdf.set_text_color(39, 174, 96)
        pdf.cell(45, 6, "STCR Grounded (ICAR Vertisol)", 0)
    else:
        pdf.set_text_color(230, 126, 34)
        pdf.cell(45, 6, "ML Estimated (Regional Fallback)", 0)

    pdf.set_text_color(45, 45, 45)
    pdf.set_y(52)

    # ── Section 2: Soil Health Summary (Table) ──
    pdf.set_font("Helvetica", "B", 11)
    pdf.cell(0, 6, "Soil Health Summary (Soil Health Card 0-5cm)", new_x="LMARGIN", new_y="NEXT")

    # Table Header
    pdf.set_fill_color(234, 229, 217)
    pdf.set_font("Helvetica", "B", 9)
    col_w = [40, 45, 55, 50]
    headers = ["Parameter", "Tested / Satellite Value", "Standard Benchmark", "Status Assessment"]
    for i, h in enumerate(headers):
        pdf.cell(col_w[i], 6, sanitize_text(h), 1, 0, "C", fill=True)
    pdf.ln()

    # Table Rows
    pdf.set_font("Helvetica", "", 9)
    rows = [
        ("Nitrogen (N)", f"{soil_data.get('N', 0):.1f} kg/ha", "280.0 kg/ha", "Deficient - Critical" if soil_data.get('N', 0) < 280 else "Sufficient"),
        ("Phosphorus (P)", f"{soil_data.get('P', 0):.1f} kg/ha", "22.0 kg/ha", "Deficient" if soil_data.get('P', 0) < 22 else "Sufficient"),
        ("Potassium (K)", f"{soil_data.get('K', 0):.1f} kg/ha", "140.0 kg/ha", "Deficient" if soil_data.get('K', 0) < 140 else "Sufficient"),
        ("Organic Carbon", f"{soil_data.get('organic_carbon', 0):.2f}%", "0.50% - 0.75%", "Low - Needs IPNS" if soil_data.get('organic_carbon', 0) < 0.5 else "Optimal"),
        ("Soil pH", f"{soil_data.get('pH', 0):.1f}", "6.5 - 7.8", "Neutral/Black Soil" if 6.5 <= soil_data.get('pH', 7) <= 8.2 else "Alert"),
    ]
    for param, val, benchmark, status in rows:
        pdf.cell(col_w[0], 5.5, sanitize_text(param), 1, 0, "L")
        pdf.cell(col_w[1], 5.5, sanitize_text(val), 1, 0, "C")
        pdf.cell(col_w[2], 5.5, sanitize_text(benchmark), 1, 0, "C")
        pdf.cell(col_w[3], 5.5, sanitize_text(status), 1, 0, "C")
        pdf.ln()

    pdf.ln(3)

    # ── Section 3: Commercial Fertilizer Action Plan (Hero Table) ──
    pdf.set_font("Helvetica", "B", 11)
    pdf.cell(0, 6, "Commercial Fertilizer Prescription (2026 GoI Subsidized MRP)", new_x="LMARGIN", new_y="NEXT")

    pdf.set_fill_color(234, 229, 217)
    pdf.set_font("Helvetica", "B", 9)
    bag_w = [35, 35, 35, 45, 40]
    bag_headers = ["Fertilizer", "Proportional Bags", "Net Weight (kg)", "Composition Spec", "Total Cost (Rs.)"]
    for i, h in enumerate(bag_headers):
        pdf.cell(bag_w[i], 6, sanitize_text(h), 1, 0, "C", fill=True)
    pdf.ln()

    pdf.set_font("Helvetica", "", 9)
    u = commercial_bags.get("urea", {})
    d = commercial_bags.get("dap", {})
    m = commercial_bags.get("mop", {})

    bag_rows = [
        ("Urea", f"{u.get('bags', 0):.2f} bags", f"{u.get('kg', 0):.1f} kg", "46% N (45 kg bag @ Rs. 242)", f"Rs. {u.get('cost', 0):,.2f}"),
        ("DAP", f"{d.get('bags', 0):.2f} bags", f"{d.get('kg', 0):.1f} kg", "18% N, 46% P2O5 (50 kg @ Rs. 1350)", f"Rs. {d.get('cost', 0):,.2f}"),
        ("MOP", f"{m.get('bags', 0):.2f} bags", f"{m.get('kg', 0):.1f} kg", "60% K2O (50 kg @ Rs. 1710)", f"Rs. {m.get('cost', 0):,.2f}"),
    ]
    for name, bg_count, net_kg, spec, cost_str in bag_rows:
        pdf.cell(bag_w[0], 5.5, sanitize_text(name), 1, 0, "L")
        pdf.cell(bag_w[1], 5.5, sanitize_text(bg_count), 1, 0, "C")
        pdf.cell(bag_w[2], 5.5, sanitize_text(net_kg), 1, 0, "C")
        pdf.cell(bag_w[3], 5.5, sanitize_text(spec), 1, 0, "L")
        pdf.cell(bag_w[4], 5.5, sanitize_text(cost_str), 1, 0, "R")
        pdf.ln()

    # Total Cost Row
    pdf.set_font("Helvetica", "B", 9)
    pdf.set_fill_color(243, 239, 230)
    pdf.cell(bag_w[0] + bag_w[1] + bag_w[2] + bag_w[3], 6, "Total Recommended Investment:", 1, 0, "R", fill=True)
    pdf.cell(bag_w[4], 6, f"Rs. {cost_comparison.get('recommended_cost', 0):,.2f}", 1, 0, "R", fill=True)
    pdf.ln()

    pdf.ln(3)

    # ── Section 4: Split Application Schedule ──
    pdf.set_font("Helvetica", "B", 11)
    pdf.cell(0, 6, "Split Application Schedule & Timing", new_x="LMARGIN", new_y="NEXT")

    pdf.set_fill_color(234, 229, 217)
    pdf.set_font("Helvetica", "B", 9)
    sch_w = [45, 55, 90]
    sch_headers = ["Crop Stage", "Recommended Mix", "Application Instructions & Timing"]
    for i, h in enumerate(sch_headers):
        pdf.cell(sch_w[i], 6, sanitize_text(h), 1, 0, "C", fill=True)
    pdf.ln()

    pdf.set_font("Helvetica", "", 8.5)
    for entry in schedule:
        stage = entry.get("stage", "")
        qty = entry.get("quantity", {})
        if isinstance(qty, dict):
            qty_summary = ", ".join([f"{k}: {v}" for k, v in qty.items() if v])
        else:
            qty_summary = str(qty)
        note = entry.get("timing_note", "")

        pdf.cell(sch_w[0], 6.5, sanitize_text(stage), 1, 0, "L")
        pdf.cell(sch_w[1], 6.5, sanitize_text(qty_summary), 1, 0, "L")
        pdf.cell(sch_w[2], 6.5, sanitize_text(note), 1, 0, "L")
        pdf.ln()

    pdf.ln(3)

    # ── Section 5: IPNS Sustainable Recommendation Note ──
    pdf.set_fill_color(243, 239, 230)
    pdf.rect(10, pdf.get_y(), 190, 15, "FD")
    pdf.set_xy(13, pdf.get_y() + 2)
    pdf.set_font("Helvetica", "B", 8.5)
    pdf.set_text_color(74, 124, 89)
    pdf.cell(0, 4, sanitize_text("IPNS Organic Blending Advisory:"), new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("Helvetica", "", 8)
    pdf.set_text_color(45, 45, 45)
    pdf.set_x(13)
    pdf.multi_cell(
        184,
        3.8,
        sanitize_text(
            "By substituting 20-25% of chemical nitrogen with Farm Yard Manure (FYM) or Vermicompost, "
            "soil organic carbon increases season-over-season, lowering future chemical fertilizer dependency and expenditure."
        ),
    )

    return bytes(pdf.output())
