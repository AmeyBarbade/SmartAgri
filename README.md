# Sustainable Fertilizer Usage Optimizer (PSAI01)

## Architecture Rationale: Hybrid STCR + ML Engine

This project builds a **hybrid recommendation engine** for fertilizer application, explicitly differentiating itself from simplistic single-classifier approaches commonly seen in agricultural hackathons. Instead of treating fertilizer recommendation as a black-box classification problem, we decouple it into a rules-driven deterministic engine with an ML-based personalization and fallback layer.

### Why a Hybrid Approach?

In agricultural science, there are established mathematical models (like ICAR's STCR - Soil Test Crop Response) that compute exact nutrient doses based on target yield and soil test values. A pure Machine Learning classifier ignores these established formulas, often hallucinating fertilizer amounts or ignoring the target yield constraint entirely.

However, STCR coefficients are not available for every crop and every soil type in every region. Our architecture elegantly solves this limitation via a two-tier routing system:

1. **The Primary Deterministic Engine (STCR Rules)**
   - When a request is made, the engine first queries a configuration-driven lookup for STCR coefficients (`a` and `b`) specific to the requested crop.
   - If coefficients exist, it calculates the dose using the deterministic formula: `Dose = (a * target_yield) - (b * soil_test_value)`.
   - The output is flagged with high confidence: `confidence: "stcr_grounded"`.
   - *Note: We intentionally decoupled these coefficients into a YAML config (`config/stcr_coefficients.yaml`) rather than hardcoding them, allowing regional agronomists to update the system without touching the Python backend.*

2. **The Secondary ML Fallback Layer**
   - If the system encounters a crop for which STCR coefficients are unknown (flagged as `coefficients pending`), it intercepts the null result and routes the input (soil NPK, pH, plus weather data) into a trained Scikit-Learn `RandomForestRegressor`.
   - The model infers a statistically probable dose based on historical Kaggle dataset patterns.
   - Crucially, the system tags this output as `confidence: "ml_estimated"`, ensuring transparency.

### The Fusion Layer
The `fusion.py` module orchestrates this handoff and adds three critical operational layers:
- **Comprehensive Micronutrient Analysis**: It checks all 12 parameters (Zinc, Boron, Sulphur, etc.) against ideal thresholds, extending beyond basic N/P/K.
- **Weather-Aware Optimization**: By hooking into the Open-Meteo API, it predicts short-term heavy rainfall and flags the schedule to prevent fertilizer leaching.
- **Economic Costing**: Calculates the ₹/acre cost of the recommended dose versus the farmer's historical usage, demonstrating clear ROI.

### Tech Stack
- **Backend**: Python, FastAPI
- **ML Layer**: Scikit-Learn (RandomForest)
- **Frontend**: Streamlit
- **External APIs**: Open-Meteo

## Running the Application

### 1. Setup Environment
```bash
pip install -r requirements.txt
```

### 2. Train the ML Fallback Model
```bash
python backend/ml_training/train.py
```

### 3. Start the Backend API
```bash
uvicorn backend.main:app --reload
```

### 4. Start the Frontend Dashboard
```bash
streamlit run frontend/app.py
```