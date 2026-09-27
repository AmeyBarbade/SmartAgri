import joblib
import pandas as pd
import logging
from pathlib import Path

logger = logging.getLogger(__name__)

MODEL_DIR = Path(__file__).parent / "models"
MODEL_PATH = MODEL_DIR / "ml_fallback_model.pkl"
FEATURES_PATH = MODEL_DIR / "ml_features.pkl"

_model = None
_features = None

def load_model():
    global _model, _features
    if _model is None:
        try:
            _model = joblib.load(MODEL_PATH)
            _features = joblib.load(FEATURES_PATH)
        except Exception as e:
            logger.error(f"Could not load ML model: {e}")
            return False
    return True

def predict_ml_dose(crop_type: str, soil_data: dict, weather_data: dict):
    if not load_model():
        return None

    # Prepare input dict
    input_data = {
        'temperature': weather_data.get('temperature', 25.0),
        'humidity': weather_data.get('humidity', 60.0),
        'rainfall': weather_data.get('rainfall', 800.0),
        'soil_N': soil_data.get('N', 0),
        'soil_P': soil_data.get('P', 0),
        'soil_K': soil_data.get('K', 0),
        'soil_pH': soil_data.get('pH', 7.0)
    }

    df = pd.DataFrame([input_data])
    
    # Handle one-hot encoding for crop type
    # We must match the features trained on
    for f in _features:
        if f.startswith("crop_type_"):
            expected_crop = f.replace("crop_type_", "")
            df[f] = 1.0 if expected_crop == crop_type.lower() else 0.0
            
    # Keep only the features used in training in the correct order
    X = df[_features]
    
    pred = _model.predict(X)[0]
    
    # pred contains N, P, K
    doses = {
        "N": max(0.0, round(pred[0], 2)),
        "P": max(0.0, round(pred[1], 2)),
        "K": max(0.0, round(pred[2], 2))
    }
    
    logger.info(f"Generated ML estimation for {crop_type}")
    return doses
