"""
SmartAgri — Sustainable Fertilizer Usage Optimizer
Frontend Dashboard (Streamlit)
Author: Amey Barbade
Phase 4: GIS Integration & Polygon Auto-Fill
"""
import sys
import json
import requests
import streamlit as st
import plotly.graph_objects as go
from pathlib import Path

# Add project root to sys.path for backend engine imports
ROOT_DIR = Path(__file__).parent.parent
sys.path.append(str(ROOT_DIR))

import folium
from folium.plugins import Draw
from streamlit_folium import st_folium
from backend.engine.gis import calculate_polygon_metrics, fetch_isric_soilgrids

API_URL = "http://127.0.0.1:8001/recommend"
SEASONS_PATH = ROOT_DIR / "backend" / "config" / "mock_seasons.json"

st.set_page_config(
    page_title="SmartAgri — Sustainable Fertilizer Optimizer",
    page_icon="🌱",
    layout="wide"
)

# ══════════════════════════════════════════════════════════════════════════
# CUSTOM CSS — Enterprise light theme (#FDFBF7 / #F3EFE6)
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
    .bag-card h2 { margin: 0 0 4px; color: #2D2D2D; font-size: 1.2rem; }
    .bag-card p  { margin: 2px 0; color: #555; font-size: .88rem; }
    .gis-panel {
        background: #F3EFE6; border: 1px solid #DDD8CC;
        border-radius: 10px; padding: 14px 18px;
        box-shadow: 0 1px 4px rgba(0,0,0,0.06);
        margin-bottom: 12px;
    }
</style>
""", unsafe_allow_html=True)

# ══════════════════════════════════════════════════════════════════════════
# HEADER
# ══════════════════════════════════════════════════════════════════════════
st.markdown(
    "<h1 style='text-align:center; color:#2D2D2D;'>"
    "🌱 SmartAgri — Sustainable Fertilizer Optimizer</h1>"
    "<p style='text-align:center; font-size:1.05rem; color:#777;'>"
    "PSAI01 &nbsp;·&nbsp; Hybrid STCR + ML Recommendation Engine &nbsp;·&nbsp; Phase 4 GIS Integration</p>"
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

# ── Session State Defaults for Auto-Fill ──
if "land_size_val" not in st.session_state:
    st.session_state["land_size_val"] = 2.0
if "lat_val" not in st.session_state:
    st.session_state["lat_val"] = 19.7500
if "lon_val" not in st.session_state:
    st.session_state["lon_val"] = 75.7100
if "soil_n_val" not in st.session_state:
    st.session_state["soil_n_val"] = 180.0
if "soil_ph_val" not in st.session_state:
    st.session_state["soil_ph_val"] = 7.2
if "soil_oc_val" not in st.session_state:
    st.session_state["soil_oc_val"] = 0.40
if "polygon_detected" not in st.session_state:
    st.session_state["polygon_detected"] = None
if "auto_fill_applied" not in st.session_state:
    st.session_state["auto_fill_applied"] = False
if "auto_fill_source" not in st.session_state:
    st.session_state["auto_fill_source"] = ""

# ──────────────────────────────────────────────────────────────────────────
# TAB 1 — Optimizer Dashboard
# ──────────────────────────────────────────────────────────────────────────
with tab_optimizer:
    st.divider()

    # ── GIS & SATELLITE FIELD BOUNDARY SECTION ──
    st.markdown("### 🛰️ Satellite Field Boundary & GIS Auto-Fill")
    st.caption(
        "Draw your parcel boundary directly on the high-resolution Esri satellite imagery below. "
        "The system will compute equal-area acreage (EPSG:6933) and query the ISRIC SoilGrids REST API to auto-fill your Soil Health Card."
    )

    gis_map_col, gis_stats_col = st.columns([2.0, 1.0])

    with gis_map_col:
        # Build Folium map with Esri World Imagery tiles
        m = folium.Map(
            location=[19.75, 75.71],
            zoom_start=7,
            tiles="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            attr="Esri"
        )
        # Enable Leaflet Draw polygon tool
        Draw(
            export=False,
            position="topleft",
            draw_options={
                "polyline": False,
                "rectangle": True,
                "circle": False,
                "circlemarker": False,
                "marker": False,
                "polygon": True
            }
        ).add_to(m)

        map_output = st_folium(
            m,
            height=370,
            width=None,
            use_container_width=True,
            returned_objects=["last_active_drawing"],
            key="satellite_map_folium"
        )

    # Detect polygon from drawing
    drawing = map_output.get("last_active_drawing") if map_output else None
    if drawing and isinstance(drawing, dict):
        poly_metrics = calculate_polygon_metrics(drawing)
        if poly_metrics:
            st.session_state["polygon_detected"] = poly_metrics

    with gis_stats_col:
        st.markdown("#### 📐 Field Geometry")
        poly = st.session_state.get("polygon_detected")

        if poly:
            st.metric("Detected Polygon Area", f"{poly['acres']} Acres")
            st.markdown(f"**Centroid:** `{poly['lat']}° N, {poly['lon']}° E`")
            st.caption(f"Area: {poly['area_sq_m']:,.1f} m² (Equal-Area EPSG:6933)")

            if st.button("✨ Auto-Fill Soil Health Card from Satellite", type="primary", use_container_width=True):
                with st.spinner("Pinging ISRIC SoilGrids API (0-5cm depth, 4s timeout)..."):
                    soil_res = fetch_isric_soilgrids(poly["lat"], poly["lon"])

                    # Inject into session state
                    st.session_state["land_size_val"] = float(poly["acres"])
                    st.session_state["lat_val"] = float(poly["lat"])
                    st.session_state["lon_val"] = float(poly["lon"])
                    st.session_state["soil_n_val"] = float(soil_res["N"])
                    st.session_state["soil_ph_val"] = float(soil_res["pH"])
                    st.session_state["soil_oc_val"] = float(soil_res["organic_carbon"])
                    st.session_state["auto_fill_applied"] = True
                    st.session_state["auto_fill_source"] = soil_res["source"]
                    st.session_state["auto_fill_fallback"] = soil_res["is_fallback"]

                    if soil_res["is_fallback"]:
                        st.toast("⚠️ ISRIC API timed out (>4s); applied Regional Vertisol simulation.", icon="⚠️")
                    else:
                        st.toast("✅ Soil data retrieved from ISRIC SoilGrids v2.0!", icon="✅")
                    st.rerun()

            if st.session_state.get("auto_fill_applied"):
                if st.session_state.get("auto_fill_fallback"):
                    st.info(f"ℹ️ {st.session_state.get('auto_fill_source')}")
                else:
                    st.success(f"✅ {st.session_state.get('auto_fill_source')}")
        else:
            st.info(
                "💡 **How to use:**\n\n"
                "1. Click the **Polygon tool (⬡)** on the top-left toolbar of the satellite map.\n"
                "2. Click vertices around your field on the map.\n"
                "3. Click the first point to close the polygon.\n\n"
                "Once closed, the acreage and satellite auto-fill button will activate."
            )

    st.divider()

    # ── MAIN RECOMMENDATION DASHBOARD GRID ──
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

        land_size = st.number_input(
            "Land Size (Acres)",
            min_value=0.01,
            step=0.5,
            key="land_size_val"
        )
        target_yield = st.number_input("Target Yield (Quintals/ha)", min_value=1.0, value=20.0, step=5.0)

        st.markdown("##### 📍 Location (for weather & soil)")
        loc_c1, loc_c2 = st.columns(2)
        lat = loc_c1.number_input("Latitude", format="%.4f", key="lat_val")
        lon = loc_c2.number_input("Longitude", format="%.4f", key="lon_val")

        with st.expander("🧪 Soil Health Card (12 Parameters)", expanded=False):
            if st.session_state.get("auto_fill_applied"):
                st.caption(f"📍 Auto-populated via {st.session_state.get('auto_fill_source')}")
            shc1, shc2 = st.columns(2)
            with shc1:
                n_val  = st.number_input("Nitrogen (N) kg/ha",  key="soil_n_val")
                p_val  = st.number_input("Phosphorus (P) kg/ha", value=15.0)
                k_val  = st.number_input("Potassium (K) kg/ha", value=200.0)
                s_val  = st.number_input("Sulphur (S) ppm",     value=8.0)
                zn_val = st.number_input("Zinc (Zn) ppm",       value=0.4)
                fe_val = st.number_input("Iron (Fe) ppm",       value=3.0)
            with shc2:
                cu_val = st.number_input("Copper (Cu) ppm",       value=0.1)
                mn_val = st.number_input("Manganese (Mn) ppm",    value=1.0)
                b_val  = st.number_input("Boron (B) ppm",         value=0.2)
                ph_val = st.number_input("pH",                    key="soil_ph_val")
                ec_val = st.number_input("EC (dS/m)",             value=0.6)
                oc_val = st.number_input("Organic Carbon (%)",    key="soil_oc_val")

        with st.expander("💰 Previous Fertilizer Usage (bags)", expanded=False):
            st.caption("Enter number of bags you previously purchased.")
            pu1, pu2, pu3 = st.columns(3)
            urea_usage = pu1.number_input("Urea bags", value=0, min_value=0, step=1)
            dap_usage  = pu2.number_input("DAP bags",  value=0, min_value=0, step=1)
            mop_usage  = pu3.number_input("MOP bags",  value=0, min_value=0, step=1)

        run = st.button("🚀 Generate Recommendation", type="primary", use_container_width=True)

    # ── RESULTS PANEL ──
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
                with st.spinner("Analyzing soil data, querying weather, running STCR + ML engine…"):
                    response = requests.post(API_URL, json=payload, timeout=15)
                    response.raise_for_status()
                    res = response.json()

                # 1. Confidence Flag
                if res["confidence"] == "stcr_grounded":
                    st.success("✅ Recommendation grounded in verified ICAR STCR equations.")
                else:
                    st.warning("⚠️ STCR coefficients unavailable for this crop. Displaying ML-estimated baseline.")

                # 2. Weather Flag
                if res["weather_flag"]:
                    st.error(f"🌧️ Weather Alert: {res['weather_reason']}")
                else:
                    st.info("⛅ Weather is clear — good window for fertilizer application.")

                st.divider()

                # 3. Commercial Bags (Hero Section)
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
                "<h3>👈 Draw your parcel above or fill in your data, then click "
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
            yaxis=dict(title="Organic Carbon (%)", gridcolor="#DDD8CC", side="left"),
            yaxis2=dict(title="Nitrogen (kg/ha)", overlaying="y", side="right", gridcolor="#DDD8CC"),
            legend=dict(orientation="h", yanchor="bottom", y=1.05, xanchor="center", x=0.5))
        st.plotly_chart(fig, use_container_width=True)

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
        "font-weight:600; color:#555;'>Coming in Phase 5</span></div>",
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
        "font-weight:600; color:#555;'>Coming in Phase 5</span></div>",
        unsafe_allow_html=True)

# ── Footer ──
st.markdown(
    "<hr style='margin-top:40px;'>"
    "<p style='text-align:center; color:#AAA; font-size:.78rem;'>"
    "SmartAgri PSAI01 &nbsp;·&nbsp; Built by Amey Barbade &nbsp;·&nbsp; "
    "Hybrid STCR + ML Engine &nbsp;·&nbsp; Phase 4 GIS Integration</p>",
    unsafe_allow_html=True)
