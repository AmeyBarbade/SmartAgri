# SmartAgri — Sustainable Fertilizer Usage Optimizer (PSAI01)
**Author & Developer:** Amey Barbade  
**Category:** AI / ML / Agritech Decision Support  

---

## Architecture Rationale: Hybrid Deterministic STCR + ML Personalization

This project builds a **hybrid recommendation engine** for precision fertilizer application, explicitly differentiating itself from simplistic single-classifier approaches commonly seen in agricultural hackathons. Instead of treating fertilizer recommendation as a black-box classification problem, we decouple it into a rules-driven deterministic engine with an ML-based personalization and fallback layer.

### Why a Hybrid Approach?

In agricultural science, there are established mathematical models (like ICAR's STCR - Soil Test Crop Response Targeted Yield equations) that compute exact nutrient doses based on target yield and soil test values. A pure Machine Learning classifier ignores these established formulas, often hallucinating fertilizer amounts or ignoring the target yield constraint entirely.

However, STCR coefficients are not available for every crop and every soil type in every region. Our architecture elegantly solves this limitation via a two-tier routing system:

1. **The Primary Deterministic Engine (Verified ICAR STCR Rules)**
   - When a request is made, the engine queries `backend/config/stcr_coefficients.yaml`.
   - Uses real verified ICAR STCR equations for Vertisols (e.g. Cotton and Soybean):
     - **Cotton**: $FN = 9.53T - 0.38S_N$, $FP_2O_5 = 6.26T - 2.66S_P$, $FK_2O = 3.89T - 0.07S_K$
     - **Soybean**: $FN = 6.77T - 0.16S_N$, $FP_2O_5 = 1.55T - 0.61S_P$, $FK_2O = 5.75T - 0.27S_K$
   - Computes targeted doses with `confidence: "stcr_grounded"`.
   - Crops without verified coefficients are flagged as `"coefficients pending"`.

2. **The Secondary ML Fallback Layer**
   - If a crop lacks STCR coefficients, the request is intercepted and routed to a trained Scikit-Learn `RandomForestRegressor`.
   - The ML model estimates doses based on weather (Open-Meteo) and soil parameters, tagging output as `confidence: "ml_estimated"`.

3. **Commercial Fertilizer Bag Converter (`bag_converter.py`)**
   - Farmers do not buy raw N, P, K; they purchase commercial bags.
   - The converter calculates:
     - **DAP (18% N, 46% P₂O₅ in 50 kg bags)**: Fulfilled first for phosphorus, supplying bonus nitrogen.
     - **Urea (46% N in 45 kg bags)**: Fulfilled next for residual nitrogen after deducting DAP's contribution.
     - **MOP (60% K₂O in 50 kg bags)**: Fulfilled for potassium.
   - Prices follow **2026 Government of India subsidized MRP**: Urea @ ₹242/bag, DAP @ ₹1350/bag, MOP @ ₹1710/bag.

4. **GIS Integration & Polygon Auto-Fill (`gis.py`)**
   - **Interactive Esri Satellite Map**: Allows farmers to trace their exact parcel boundary using Leaflet Draw.
   - **Equal-Area Projection**: Transforms coordinates from `EPSG:4326` to `EPSG:6933` (equal-area cylindrical) via `pyproj` to calculate true geodesic acreage.
   - **Centroid Extraction & ISRIC SoilGrids API**: Queries the global ISRIC REST API (0–5cm depth) to retrieve Nitrogen, pH, and Organic Carbon.
   - **Failsafe Demo Mode**: Strict 4-second timeout; if the global API delays, it falls back to a regional Vertisol simulation and alerts the user.

5. **Multi-Season Tracking & IPNS Organic Blending**
   - **Soil Health Trajectory**: Visualizes Organic Carbon (%) and Nitrogen trends over multiple seasons.
   - **IPNS (Integrated Plant Nutrient System)**: Recommends replacing 25% chemical Nitrogen with Farm Yard Manure (FYM) or Vermicompost.

---

## Tech Stack
- **Backend**: Python 3.12, FastAPI, Uvicorn
- **Geospatial & GIS**: Folium, Streamlit-Folium, Shapely, PyProj (EPSG:6933)
- **ML Layer**: Scikit-Learn (RandomForestRegressor)
- **Frontend**: Streamlit (Light Enterprise Theme, Plotly Charts)
- **External APIs**: Open-Meteo Weather API, ISRIC SoilGrids v2.0 REST API

---

## Running the Application

### 1. Setup Environment
```bash
pip install -r requirements.txt
```

### 2. Train the ML Fallback Model
```bash
python backend/ml_training/train.py
```

### 3. Start the Backend API (Port 8001)
```bash
python -m uvicorn main:app --reload --port 8001
```

### 4. Start the Frontend Dashboard (Port 8501)
```bash
python -m streamlit run frontend/app.py
```
Open **http://localhost:8501** in your browser.