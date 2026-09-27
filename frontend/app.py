"""
SmartAgri — Sustainable Fertilizer Usage Optimizer
Frontend Dashboard (Streamlit)
Author: Amey Barbade
"""
import streamlit as st
import requests
import json
import plotly.graph_objects as go
from pathlib import Path

API_URL = "http://127.0.0.1:8001/recommend"
SEASONS_PATH = Path(__file__).parent.parent / "backend" / "config" / "mock_seasons.json"

st.set_page_config(
    page_title="SmartAgri — Sustainable Fertilizer Optimizer",
    page_icon="🌱",
    layout="wide"
)

# ══════════════════════════════════════════════════════════════════════════
# CUSTOM CSS — enterprise light theme
# ══════════════════════════════════════════════════════════════════════════
st.markdown("""
<style>
    .stApp { background-color: #FDFBF7; }
    div[data-testid="stMetric"] {
        background-color: #F3EFE6; border: 1px solid #DDD8CC;
        border-radius: 10px; padding: 16px 20px;
        box-shadow: 0 1px 4px rgba(0,0,0,0.06);
    }
    div[data-testid="stMetricLabel"]  { color: #555; font-weight: 600; }
    div[data-testid="stMetricValue"]  { color: #2D2D2D; }
    div[data-testid="stExpander"]     { background-color: #F3EFE6;
        border: 1px solid #DDD8CC; border-radius: 8px; }
    button[data-baseweb="tab"]        { font-weight: 600; font-size: .95rem; color: #555; }
    button[data-baseweb="tab"][aria-selected="true"] {
        color: #4A7C59; border-bottom-color: #4A7C59; }
    hr { border-color: #DDD8CC; }
    #MainMenu, footer, header { visibility: hidden; }
    .author-badge { text-align:center; color:#888; font-size:.8rem; padding-bottom:8px; }
    .bag-card {
        background: #F3EFE6; border: 1px solid #DDD8CC;
        border-radius: 10px; padding: 18px; text-align: center;
        box-shadow: 0 1px 4px rgba(0,0,0,0.06);
    }
    .bag-card h2 { margin: 0 0 4px; color: #2D2D2D; }
    .bag-card p  { margin: 2px 0; color: #555; font-size: .88rem; }
</style>
""", unsafe_allow_html=True)

# ══════════════════════════════════════════════════════════════════════════
# HEADER
# ══════════════════════════════════════════════════════════════════════════
st.markdown(
    "<h1 style='text-align:center; color:#2D2D2D;'>"
    "🌱 SmartAgri — Sustainable Fertilizer Optimizer</h1>"
    "<p style='text-align:center; font-size:1.05rem; color:#777;'>"
    "PSAI01 &nbsp;·&nbsp; Hybrid STCR + ML Recommendation Engine</p>"
    "<p class='author-badge'>Developed by <strong>Amey Barbade</strong></p>",
    unsafe_allow_html=True
)

# ══════════════════════════════════════════════════════════════════════════
# TOP NAVIGATION
# ══════════════════════════════════════════════════════════════════════════
tab_optimizer, tab_tracking, tab_dealer, tab_settings = st.tabs([
    "🧪 Optimizer Dashboard",
    "📈 Multi-Season Tracking",
    "📍 Input Dealer Locator",
    "⚙️ Settings & Profiles"
])

# ──────────────────────────────────────────────────────────────────────────
# TAB 1 — Optimizer Dashboard
# ──────────────────────────────────────────────────────────────────────────
with tab_optimizer:
    st.divider()
    input_col, spacer, result_col = st.columns([1.2, 0.1, 2])

    with input_col:
        st.subheader("🌾 Crop & Location")
        crop_type = st.selectbox("Crop Type", ["cotton", "soybean", "wheat", "rice", "maize"])
        status_map = {
            "cotton": "✅ Verified ICAR STCR (Vertisols)",
            "soybean": "✅ Verified ICAR STCR (Vertisols)",
            "wheat": "⚠️ Stubbed coefficients (unverified)",
            "rice": "⚠️ Stubbed coefficients (unverified)",
            "maize": "🔴 No coefficients — ML fallback"
        }
        st.caption(status_map.get(crop_type, ""))

        land_size = st.number_input("Land Size (Acres)", min_value=0.1, value=2.0, step=0.5)
        target_yield = st.number_input("Target Yield (Quintals/ha)", min_value=1.0, value=20.0, step=5.0)

        st.markdown("##### 📍 Location (for weather)")
        loc_c1, loc_c2 = st.columns(2)
        lat = loc_c1.number_input("Latitude", value=20.5937, format="%.4f")
        lon = loc_c2.number_input("Longitude", value=78.9629, format="%.4f")

        with st.expander("🧪 Soil Health Card (12 Parameters)", expanded=False):
            shc1, shc2 = st.columns(2)
            with shc1:
                n_val  = st.number_input("Nitrogen (N) kg/ha",  value=180.0)
                p_val  = st.number_input("Phosphorus (P) kg/ha", value=15.0)
                k_val  = st.number_input("Potassium (K) kg/ha", value=200.0)
                s_val  = st.number_input("Sulphur (S) ppm",     value=8.0)
                zn_val = st.number_input("Zinc (Zn) ppm",       value=0.4)
                fe_val = st.number_input("Iron (Fe) ppm",       value=3.0)
            with shc2:
                cu_val = st.number_input("Copper (Cu) ppm",       value=0.1)
                mn_val = st.number_input("Manganese (Mn) ppm",    value=1.0)
                b_val  = st.number_input("Boron (B) ppm",         value=0.2)
                ph_val = st.number_input("pH",                    value=7.2)
                ec_val = st.number_input("EC (dS/m)",             value=0.6)
                oc_val = st.number_input("Organic Carbon (%)",    value=0.4)

        with st.expander("💰 Previous Fertilizer Usage (bags)", expanded=False):
            st.caption("Enter number of bags you previously purchased.")
            pu1, pu2, pu3 = st.columns(3)
            urea_usage = pu1.number_input("Urea bags", value=0, min_value=0, step=1)
            dap_usage  = pu2.number_input("DAP bags",  value=0, min_value=0, step=1)
            mop_usage  = pu3.number_input("MOP bags",  value=0, min_value=0, step=1)

        run = st.button("🚀 Generate Recommendation", type="primary", use_container_width=True)

    # ── RESULTS ──
    with result_col:
        if run:
            payload = {
                "crop_type": crop_type,
                "land_size_acres": land_size,
                "target_yield": target_yield,
                "location": {"lat": lat, "lon": lon},
                "previous_fertilizer_usage": {"Urea": urea_usage, "DAP": dap_usage, "MOP": mop_usage},
                "soil_data": {
                    "N": n_val, "P": p_val, "K": k_val, "S": s_val,
                    "Zn": zn_val, "Fe": fe_val, "Cu": cu_val, "Mn": mn_val,
                    "B": b_val, "pH": ph_val, "EC": ec_val, "organic_carbon": oc_val
                }
            }

            try:
                with st.spinner("Analyzing soil data, querying weather, running engine…"):
                    response = requests.post(API_URL, json=payload, timeout=15)
                    response.raise_for_status()
                    res = response.json()

                # 1. Confidence
                if res["confidence"] == "stcr_grounded":
                    st.success("✅ Recommendation grounded in verified ICAR STCR equations.")
                else:
                    st.warning("⚠️ STCR coefficients unavailable for this crop. Displaying ML-estimated baseline.")

                # 2. Weather
                if res["weather_flag"]:
                    st.error(f"🌧️ Weather Alert: {res['weather_reason']}")
                else:
                    st.info("⛅ Weather is clear — good window for fertilizer application.")

                st.divider()

                # 3. COMMERCIAL BAG RECOMMENDATION (the new hero section)
                st.subheader("🛒 What to Buy — Commercial Fertilizer Bags")
                bags = res.get("commercial_bags", {})
                bc1, bc2, bc3 = st.columns(3)

                with bc1:
                    u = bags.get("urea", {})
                    st.markdown(
                        f"<div class='bag-card'>"
                        f"<h2>🟡 Urea</h2>"
                        f"<h1 style='color:#4A7C59; margin:8px 0;'>{u.get('bags', 0)} bags</h1>"
                        f"<p>{u.get('bag_spec', '')}</p>"
                        f"<p>N supplied: {u.get('n_supplied_kg', 0)} kg</p>"
                        f"<p style='font-weight:700; color:#2D2D2D;'>₹{u.get('cost', 0):,}</p>"
                        f"</div>", unsafe_allow_html=True)
                with bc2:
                    d = bags.get("dap", {})
                    st.markdown(
                        f"<div class='bag-card'>"
                        f"<h2>🟤 DAP</h2>"
                        f"<h1 style='color:#4A7C59; margin:8px 0;'>{d.get('bags', 0)} bags</h1>"
                        f"<p>{d.get('bag_spec', '')}</p>"
                        f"<p>P₂O₅ supplied: {d.get('p2o5_supplied_kg', 0)} kg</p>"
                        f"<p>Bonus N: {d.get('n_supplied_kg', 0)} kg</p>"
                        f"<p style='font-weight:700; color:#2D2D2D;'>₹{d.get('cost', 0):,}</p>"
                        f"</div>", unsafe_allow_html=True)
                with bc3:
                    m = bags.get("mop", {})
                    st.markdown(
                        f"<div class='bag-card'>"
                        f"<h2>🔴 MOP</h2>"
                        f"<h1 style='color:#4A7C59; margin:8px 0;'>{m.get('bags', 0)} bags</h1>"
                        f"<p>{m.get('bag_spec', '')}</p>"
                        f"<p>K₂O supplied: {m.get('k2o_supplied_kg', 0)} kg</p>"
                        f"<p style='font-weight:700; color:#2D2D2D;'>₹{m.get('cost', 0):,}</p>"
                        f"</div>", unsafe_allow_html=True)

                st.divider()

                # 4. Cost Economics
                st.subheader("💰 Cost Economics (2026 GoI MRP)")
                costs = res["cost_comparison"]
                mc1, mc2, mc3 = st.columns(3)
                mc1.metric("Recommended Cost (₹)", f"₹{costs['recommended_cost']:,.0f}")

                current_cost = costs["current_usage_cost"]
                if current_cost > 0:
                    mc2.metric("Your Current Cost (₹)", f"₹{current_cost:,.0f}")
                    savings = costs["savings"]
                    mc3.metric("Estimated Savings (₹)", f"₹{abs(savings):,.0f}",
                               delta=f"₹{savings:,.0f}", delta_color="normal")
                else:
                    mc2.metric("Your Current Cost (₹)", "₹0 (No prior usage)")
                    mc3.metric("Projected Investment (₹)",
                               f"₹{costs['recommended_cost']:,.0f}", delta=None)

                st.divider()

                # 5. Explainability Chart
                st.subheader("📊 Soil Deficiency Analysis")
                explain = res.get("explainability", {})
                if explain:
                    nutrients = list(explain.keys())
                    actuals = [explain[n]["actual"] for n in nutrients]
                    targets = [explain[n]["target"] for n in nutrients]
                    fig = go.Figure()
                    fig.add_trace(go.Bar(
                        name="Your Soil (Actual)", x=nutrients, y=actuals,
                        marker_color=["#C0392B" if a < t else "#27AE60"
                                      for a, t in zip(actuals, targets)]))
                    fig.add_trace(go.Bar(
                        name="Target (Ideal)", x=nutrients, y=targets,
                        marker_color="#B0A89A", opacity=0.55))
                    fig.update_layout(
                        barmode="group", xaxis_title="Nutrient",
                        yaxis_title="Value", height=350,
                        margin=dict(t=30, b=40),
                        legend=dict(orientation="h", yanchor="bottom",
                                    y=1.02, xanchor="right", x=1),
                        plot_bgcolor="#FDFBF7", paper_bgcolor="#FDFBF7",
                        font=dict(color="#2D2D2D"),
                        xaxis=dict(gridcolor="#DDD8CC"),
                        yaxis=dict(gridcolor="#DDD8CC"))
                    st.plotly_chart(fig, use_container_width=True)

                st.divider()

                # 6. Nutrient Shortfall
                st.subheader("🧪 Nutrient Shortfall (N / P₂O₅ / K₂O)")
                shortfall = res["nutrient_shortfall"]
                dose_items = {k: v for k, v in shortfall.items() if isinstance(v, (int, float))}
                flag_items = {k: v for k, v in shortfall.items() if isinstance(v, str)}
                if dose_items:
                    d1, d2, d3 = st.columns(3)
                    cols = [d1, d2, d3]
                    for i, (k, v) in enumerate(dose_items.items()):
                        cols[i % 3].metric(f"{k} (kg)", f"{v:,.2f}")
                if flag_items:
                    for k, v in flag_items.items():
                        st.warning(f"⚠️ **{k}**: {v}")

                st.divider()

                # 7. Application Schedule
                st.subheader("📅 Application Schedule")
                for entry in res["application_schedule"]:
                    qty = entry["quantity"]
                    qty_str = " · ".join([f"{k}: {v}" for k, v in qty.items()])
                    st.markdown(
                        f"**{entry['stage']}** — {entry['fertilizer']}  \n"
                        f"&emsp; {qty_str}  \n"
                        f"&emsp; 📝 _{entry['timing_note']}_")

                # 8. IPNS
                ipns = res.get("ipns_alternative")
                if ipns:
                    st.divider()
                    st.subheader("🌿 IPNS Organic Blending Alternative")
                    st.info(
                        f"**Integrated Plant Nutrient System (IPNS)**: "
                        f"Replace 25% of chemical N with organic sources.\n\n"
                        f"- Chemical N retained: **{ipns['chemical_n_kg']} kg**\n"
                        f"- FYM / Vermicompost required: **{ipns['fym_vermicompost_kg']} kg**\n\n"
                        f"_{ipns['note']}_")

            except requests.exceptions.ConnectionError:
                st.error("❌ Cannot connect to backend API. Make sure FastAPI is running on port 8001.")
            except Exception as e:
                st.error(f"❌ Error: {e}")
        else:
            st.markdown(
                "<div style='text-align:center; padding:80px 0; color:#999;'>"
                "<h3>👈 Fill in your crop and soil data, then click "
                "<em>Generate Recommendation</em></h3></div>",
                unsafe_allow_html=True)

# ──────────────────────────────────────────────────────────────────────────
# TAB 2 — Multi-Season Tracking
# ──────────────────────────────────────────────────────────────────────────
with tab_tracking:
    st.divider()
    st.subheader("📈 Multi-Season Soil Health Tracking")

    try:
        with open(SEASONS_PATH, "r") as f:
            season_data = json.load(f)

        st.markdown(f"**Farm:** {season_data['farmer']}")

        seasons = season_data["seasons"]
        labels  = [s["season"] for s in seasons]
        oc_vals = [s["organic_carbon"] for s in seasons]
        n_vals  = [s["soil_N"] for s in seasons]

        fig = go.Figure()
        fig.add_trace(go.Scatter(
            x=labels, y=oc_vals, name="Organic Carbon (%)",
            mode="lines+markers", marker=dict(size=10),
            line=dict(color="#4A7C59", width=3)))
        fig.add_trace(go.Scatter(
            x=labels, y=n_vals, name="Nitrogen (kg/ha)",
            mode="lines+markers", marker=dict(size=10),
            line=dict(color="#C0392B", width=3), yaxis="y2"))
        fig.update_layout(
            height=400,
            plot_bgcolor="#FDFBF7", paper_bgcolor="#FDFBF7",
            font=dict(color="#2D2D2D"),
            xaxis=dict(title="Season", gridcolor="#DDD8CC"),
            yaxis=dict(title="Organic Carbon (%)", gridcolor="#DDD8CC",
                       side="left"),
            yaxis2=dict(title="Nitrogen (kg/ha)", overlaying="y",
                        side="right", gridcolor="#DDD8CC"),
            legend=dict(orientation="h", yanchor="bottom", y=1.05,
                        xanchor="center", x=0.5))
        st.plotly_chart(fig, use_container_width=True)

        # Season detail table
        st.markdown("#### Season Details")
        for s in seasons:
            with st.expander(f"{s['season']} — {s['crop']}", expanded=False):
                c1, c2, c3 = st.columns(3)
                c1.metric("Yield Achieved", f"{s['yield_achieved']} q/ha")
                c2.metric("Organic Carbon", f"{s['organic_carbon']}%")
                c3.metric("Soil N", f"{s['soil_N']} kg/ha")
                bags = s["fertilizer_bags"]
                st.caption(f"Bags used: Urea × {bags['Urea']}, DAP × {bags['DAP']}, MOP × {bags['MOP']}")

        st.divider()
        st.markdown(
            "**How SmartAgri uses this data:** The platform tracks soil organic carbon "
            "and macronutrient levels across seasons. As organic carbon improves (due to "
            "IPNS practices like FYM application), the engine progressively reduces the "
            "chemical fertilizer recommendation in future seasons — creating a positive "
            "feedback loop that lowers input costs and improves soil health over time. "
            "This historical context is what distinguishes a decision-support system from "
            "a one-shot calculator.")

    except FileNotFoundError:
        st.info("Season tracking data not yet available. Run the platform for a few seasons to see trends.")

# ──────────────────────────────────────────────────────────────────────────
# TAB 3 — Dealer Locator (placeholder)
# ──────────────────────────────────────────────────────────────────────────
with tab_dealer:
    st.divider()
    st.markdown(
        "<div style='text-align:center; padding:100px 40px; color:#888;'>"
        "<h2>📍 Input Dealer Locator</h2>"
        "<p style='font-size:1.1rem;'>Find the nearest fertilizer dealers and "
        "agri-input shops based on your location.</p><br>"
        "<span style='background:#EAE5D9; padding:8px 20px; border-radius:6px; "
        "font-weight:600; color:#555;'>Coming in Phase 4</span></div>",
        unsafe_allow_html=True)

# ──────────────────────────────────────────────────────────────────────────
# TAB 4 — Settings (placeholder)
# ──────────────────────────────────────────────────────────────────────────
with tab_settings:
    st.divider()
    st.markdown(
        "<div style='text-align:center; padding:100px 40px; color:#888;'>"
        "<h2>⚙️ Settings & Profiles</h2>"
        "<p style='font-size:1.1rem;'>Manage farm profiles, saved soil reports, "
        "and application preferences.</p><br>"
        "<span style='background:#EAE5D9; padding:8px 20px; border-radius:6px; "
        "font-weight:600; color:#555;'>Coming in Phase 4</span></div>",
        unsafe_allow_html=True)

# ── Footer ──
st.markdown(
    "<hr style='margin-top:40px;'>"
    "<p style='text-align:center; color:#AAA; font-size:.78rem;'>"
    "SmartAgri PSAI01 &nbsp;·&nbsp; Built by Amey Barbade &nbsp;·&nbsp; "
    "Hybrid STCR + ML Engine &nbsp;·&nbsp; Phase 3</p>",
    unsafe_allow_html=True)
