import streamlit as st
import requests

API_URL = "http://127.0.0.1:8000/recommend"

st.set_page_config(page_title="Sustainable Fertilizer Optimizer", layout="wide")

st.title("🌱 Sustainable Fertilizer Usage Optimizer")
st.markdown("### PSAI01 - Phase 1 Demo (Hybrid STCR + ML Engine)")

col1, col2, col3 = st.columns(3)

with col1:
    st.header("Crop & Land Info")
    crop_type = st.selectbox("Crop Type", ["wheat", "rice", "maize", "cotton"])
    st.caption("Note: Wheat and Rice have STCR rules stubbed. Maize and Cotton will fallback to ML.")
    land_size = st.number_input("Land Size (Acres)", min_value=0.1, value=1.0)
    target_yield = st.number_input("Target Yield (Quintals/ha)", min_value=1.0, value=40.0)
    
    st.header("Location (Weather)")
    lat = st.number_input("Latitude", value=20.5937)
    lon = st.number_input("Longitude", value=78.9629)
    
    st.header("Current Usage (kg)")
    urea_usage = st.number_input("Urea", value=0.0)
    dap_usage = st.number_input("DAP", value=0.0)
    mop_usage = st.number_input("MOP", value=0.0)

with col2:
    st.header("Soil Health Card (12 Parameters)")
    n_val = st.number_input("Nitrogen (N) kg/ha", value=120.0)
    p_val = st.number_input("Phosphorus (P) kg/ha", value=15.0)
    k_val = st.number_input("Potassium (K) kg/ha", value=150.0)
    s_val = st.number_input("Sulphur (S) ppm", value=8.0)
    zn_val = st.number_input("Zinc (Zn) ppm", value=0.4)
    fe_val = st.number_input("Iron (Fe) ppm", value=3.0)
    
with col3:
    st.header("...")
    cu_val = st.number_input("Copper (Cu) ppm", value=0.1)
    mn_val = st.number_input("Manganese (Mn) ppm", value=1.0)
    b_val = st.number_input("Boron (B) ppm", value=0.2)
    ph_val = st.number_input("pH", value=7.2)
    ec_val = st.number_input("EC", value=0.6)
    oc_val = st.number_input("Organic Carbon (%)", value=0.4)

if st.button("Generate Recommendation", type="primary"):
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
        with st.spinner("Analyzing rules & ML layer..."):
            response = requests.post(API_URL, json=payload)
            response.raise_for_status()
            res = response.json()
            
            st.success("Analysis Complete!")
            
            # 1. Confidence Flag
            conf_color = "green" if res["confidence"] == "stcr_grounded" else "orange"
            st.markdown(f"**Confidence Level:** <span style='color:{conf_color}'>{res['confidence'].upper()}</span>", unsafe_allow_html=True)
            if res["confidence"] == "ml_estimated":
                st.warning("STCR coefficients pending for this crop. Displaying ML estimated dose.")
            
            # 2. Weather Flag
            if res["weather_flag"]:
                st.error(f"⚠️ Weather Alert: {res['weather_reason']}")
            else:
                st.info("⛅ Weather looks clear for application.")
                
            # 3. Nutrient Shortfall
            st.subheader("Nutrient Shortfall & Required Dose")
            st.json(res["nutrient_shortfall"])
            
            # 4. Schedule
            st.subheader("Application Schedule")
            st.table(res["application_schedule"])
            
            # 5. Cost Comparison
            st.subheader("Cost Economics")
            costs = res["cost_comparison"]
            col_c1, col_c2, col_c3 = st.columns(3)
            col_c1.metric("Recommended Cost (₹)", costs["recommended_cost"])
            col_c2.metric("Your Usage Cost (₹)", costs["current_usage_cost"])
            col_c3.metric("Estimated Savings (₹)", costs["savings"], delta=costs["savings"])
            
    except requests.exceptions.ConnectionError:
        st.error("Cannot connect to backend API. Please make sure FastAPI is running.")
    except Exception as e:
        st.error(f"Error: {e}")
