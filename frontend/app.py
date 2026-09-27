import streamlit as st
import requests
import plotly.graph_objects as go

API_URL = "http://127.0.0.1:8001/recommend"

st.set_page_config(page_title="Sustainable Fertilizer Optimizer", layout="wide")

# ── Header ──
st.markdown(
    "<h1 style='text-align:center;'>🌱 Sustainable Fertilizer Usage Optimizer</h1>"
    "<p style='text-align:center; font-size:1.1rem; color:grey;'>"
    "PSAI01 — Hybrid STCR + ML Recommendation Engine</p>",
    unsafe_allow_html=True
)
st.divider()

# ══════════════════════════════════════════════════════════════════════════
# INPUT SECTION — Primary inputs front-and-center; SHC in expander
# ══════════════════════════════════════════════════════════════════════════

input_col, spacer, result_col = st.columns([1.2, 0.1, 2])

with input_col:
    st.subheader("🌾 Crop & Location")
    crop_type = st.selectbox("Crop Type", ["wheat", "rice", "maize", "cotton"])
    if crop_type in ("wheat", "rice"):
        st.caption("✅ STCR rules available (stubbed coefficients)")
    else:
        st.caption("⚠️ No STCR coefficients — will use ML fallback")

    land_size = st.number_input("Land Size (Acres)", min_value=0.1, value=1.0, step=0.5)
    target_yield = st.number_input("Target Yield (Quintals/ha)", min_value=1.0, value=40.0, step=5.0)

    st.markdown("##### 📍 Location (for weather)")
    loc_c1, loc_c2 = st.columns(2)
    lat = loc_c1.number_input("Latitude", value=20.5937, format="%.4f")
    lon = loc_c2.number_input("Longitude", value=78.9629, format="%.4f")

    # ── Soil Health Card in a collapsible expander ──
    with st.expander("🧪 Soil Health Card (12 Parameters)", expanded=False):
        shc1, shc2 = st.columns(2)
        with shc1:
            n_val  = st.number_input("Nitrogen (N) kg/ha", value=120.0)
            p_val  = st.number_input("Phosphorus (P) kg/ha", value=15.0)
            k_val  = st.number_input("Potassium (K) kg/ha", value=150.0)
            s_val  = st.number_input("Sulphur (S) ppm", value=8.0)
            zn_val = st.number_input("Zinc (Zn) ppm", value=0.4)
            fe_val = st.number_input("Iron (Fe) ppm", value=3.0)
        with shc2:
            cu_val = st.number_input("Copper (Cu) ppm", value=0.1)
            mn_val = st.number_input("Manganese (Mn) ppm", value=1.0)
            b_val  = st.number_input("Boron (B) ppm", value=0.2)
            ph_val = st.number_input("pH", value=7.2)
            ec_val = st.number_input("EC (dS/m)", value=0.6)
            oc_val = st.number_input("Organic Carbon (%)", value=0.4)

    # ── Previous fertilizer usage ──
    with st.expander("💰 Previous Fertilizer Usage (kg)", expanded=False):
        pu1, pu2, pu3 = st.columns(3)
        urea_usage = pu1.number_input("Urea", value=0.0, min_value=0.0)
        dap_usage  = pu2.number_input("DAP", value=0.0, min_value=0.0)
        mop_usage  = pu3.number_input("MOP", value=0.0, min_value=0.0)

    run = st.button("🚀 Generate Recommendation", type="primary", use_container_width=True)

# ══════════════════════════════════════════════════════════════════════════
# RESULTS PANEL
# ══════════════════════════════════════════════════════════════════════════

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

            # ── 1. CONFIDENCE FLAG (top of results) ──
            if res["confidence"] == "stcr_grounded":
                st.success("✅ Recommendation grounded in precise STCR rules for this region.")
            else:
                st.warning("⚠️ STCR coefficients unavailable for this crop. Displaying ML-estimated baseline.")

            # ── 2. WEATHER FLAG ──
            if res["weather_flag"]:
                st.error(f"🌧️ Weather Alert: {res['weather_reason']}")
            else:
                st.info("⛅ Weather is clear — good window for fertilizer application.")

            st.divider()

            # ── 3. COST ECONOMICS (st.metric cards) ──
            st.subheader("💰 Cost Economics")
            costs = res["cost_comparison"]
            mc1, mc2, mc3 = st.columns(3)

            mc1.metric("Recommended Cost (₹)", f"₹{costs['recommended_cost']:,.2f}")

            current_cost = costs["current_usage_cost"]
            if current_cost > 0:
                # Normal savings view
                mc2.metric("Your Current Cost (₹)", f"₹{current_cost:,.2f}")
                savings = costs["savings"]
                mc3.metric(
                    "Estimated Savings (₹)",
                    f"₹{abs(savings):,.2f}",
                    delta=f"₹{savings:,.2f}",
                    delta_color="normal"
                )
            else:
                # Zero-usage edge case → show as projected investment
                mc2.metric("Your Current Cost (₹)", "₹0.00 (No prior usage)")
                mc3.metric(
                    "Projected Investment (₹)",
                    f"₹{costs['recommended_cost']:,.2f}",
                    delta=None
                )

            st.divider()

            # ── 4. EXPLAINABILITY CHART ──
            st.subheader("📊 Soil Deficiency Analysis")
            explain = res.get("explainability", {})
            if explain:
                nutrients = list(explain.keys())
                actuals = [explain[n]["actual"] for n in nutrients]
                targets = [explain[n]["target"] for n in nutrients]

                fig = go.Figure()
                fig.add_trace(go.Bar(
                    name="Your Soil (Actual)",
                    x=nutrients, y=actuals,
                    marker_color=["#ef4444" if a < t else "#22c55e" for a, t in zip(actuals, targets)]
                ))
                fig.add_trace(go.Bar(
                    name="Target (Ideal)",
                    x=nutrients, y=targets,
                    marker_color="#94a3b8",
                    opacity=0.5
                ))
                fig.update_layout(
                    barmode="group",
                    xaxis_title="Nutrient",
                    yaxis_title="Value",
                    height=350,
                    margin=dict(t=30, b=40),
                    legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1)
                )
                st.plotly_chart(fig, use_container_width=True)

            st.divider()

            # ── 5. NUTRIENT SHORTFALL TABLE ──
            st.subheader("🧪 Nutrient Shortfall & Required Dose")
            shortfall = res["nutrient_shortfall"]
            # Separate numeric doses from textual flags
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

            # ── 6. APPLICATION SCHEDULE ──
            st.subheader("📅 Application Schedule")
            for entry in res["application_schedule"]:
                qty = entry["quantity_kg"]
                qty_str = ", ".join([f"{k}: {v} kg" for k, v in qty.items() if v > 0])
                st.markdown(
                    f"**{entry['stage']}** — {entry['fertilizer']}  \n"
                    f"&emsp; Quantity: {qty_str}  \n"
                    f"&emsp; 📝 _{entry['timing_note']}_"
                )

            # ── 7. IPNS ORGANIC BLENDING ──
            ipns = res.get("ipns_alternative")
            if ipns:
                st.divider()
                st.subheader("🌿 IPNS Organic Blending Alternative")
                st.info(
                    f"**Integrated Plant Nutrient System (IPNS)**: "
                    f"Replace 25% of chemical Nitrogen with organic sources.\n\n"
                    f"- Chemical N retained: **{ipns['chemical_n_kg']} kg**\n"
                    f"- FYM / Vermicompost required: **{ipns['fym_vermicompost_kg']} kg**\n\n"
                    f"_{ipns['note']}_"
                )

        except requests.exceptions.ConnectionError:
            st.error("❌ Cannot connect to backend API. Please make sure FastAPI is running on port 8001.")
        except Exception as e:
            st.error(f"❌ Error: {e}")
    else:
        st.markdown(
            "<div style='text-align:center; padding:80px 0; color:grey;'>"
            "<h3>👈 Fill in your crop and soil data, then click <em>Generate Recommendation</em></h3>"
            "</div>",
            unsafe_allow_html=True
        )
