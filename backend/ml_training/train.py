import pandas as pd
import numpy as np
import joblib
from sklearn.ensemble import RandomForestRegressor
from pathlib import Path
import os

# Train on the public Kaggle crop/fertilizer dataset schema as fallback
# (N, P, K, temperature, humidity, pH, rainfall -> crop/fertilizer)

def generate_synthetic_data(num_samples=1000):
    """
    Generate synthetic data matching the Kaggle schema to keep the training
    script runnable without downloading a CSV manually.
    """
    np.random.seed(42)
    crops = ['maize', 'cotton', 'sugarcane', 'soybean']
    
    data = {
        'crop_type': np.random.choice(crops, num_samples),
        'temperature': np.random.uniform(20, 35, num_samples),
        'humidity': np.random.uniform(30, 80, num_samples),
        'rainfall': np.random.uniform(500, 1500, num_samples),
        'soil_N': np.random.uniform(10, 100, num_samples),
        'soil_P': np.random.uniform(5, 50, num_samples),
        'soil_K': np.random.uniform(10, 80, num_samples),
        'soil_pH': np.random.uniform(5.5, 8.5, num_samples)
    }
    
    df = pd.DataFrame(data)
    
    # Simulate a target: needed fertilizer based on soil and crop
    # This is a dummy correlation for the fallback ML model
    crop_factor = df['crop_type'].map({'maize': 1.2, 'cotton': 1.0, 'sugarcane': 1.5, 'soybean': 0.8})
    
    df['target_N_dose'] = (150 * crop_factor) - (df['soil_N'] * 0.8) + np.random.normal(0, 5, num_samples)
    df['target_P_dose'] = (60 * crop_factor) - (df['soil_P'] * 0.9) + np.random.normal(0, 3, num_samples)
    df['target_K_dose'] = (80 * crop_factor) - (df['soil_K'] * 0.7) + np.random.normal(0, 4, num_samples)
    
    # ensure non-negative
    for c in ['target_N_dose', 'target_P_dose', 'target_K_dose']:
        df[c] = df[c].clip(lower=0)
        
    return df

def train_model():
    print("Generating dataset...")
    df = generate_synthetic_data()
    
    # Features
    X = df[['temperature', 'humidity', 'rainfall', 'soil_N', 'soil_P', 'soil_K', 'soil_pH', 'crop_type']]
    X = pd.get_dummies(X, columns=['crop_type'])
    
    # Targets
    y = df[['target_N_dose', 'target_P_dose', 'target_K_dose']]
    
    print("Training ML Personalization layer...")
    model = RandomForestRegressor(n_estimators=50, random_state=42)
    model.fit(X, y)
    
    # Save the model and columns
    model_dir = Path(__file__).parent.parent / "engine" / "models"
    model_dir.mkdir(parents=True, exist_ok=True)
    
    joblib.dump(model, model_dir / "ml_fallback_model.pkl")
    joblib.dump(list(X.columns), model_dir / "ml_features.pkl")
    print(f"Model saved to {model_dir}")

if __name__ == "__main__":
    train_model()
