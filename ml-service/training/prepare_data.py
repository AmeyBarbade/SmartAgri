"""Raw LDS files -> validated, cleaned, feature-engineered training table.

Usage (from ml-service/):  python -m training.prepare_data
Outputs: data/processed/lds_wheat_rice_2018.csv, ml-service/reports/data_validation.json
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pandas as pd

from app.features import FEATURES, LEAKAGE_EXCLUDED, NUMERIC_FEATURES, TARGET
from training.datasets import DATASETS, PROCESSED_DIR, PROCESSED_FILE
from training.download_data import md5_of
from training.lds import clean, tidy, validate_raw

REPORTS_DIR = Path(__file__).resolve().parents[1] / "reports"

# Post-sowing / post-harvest numeric fields we deliberately exclude; their association with yield is
# reported to show what a leaky model would have been exploiting.
LEAKY_NUMERIC_PROBES = ["L-cropDurationDays", "I-q5512_lodgingPercent", "G-q5305_irrigTimes"]


def main() -> int:
    validation: dict = {"datasets": {}, "cleaning": {}, "leakage_probes": {}}
    frames = []
    for ds in DATASETS:
        if not ds.path.exists():
            raise SystemExit(f"{ds.path} missing - run: python -m training.download_data")
        if md5_of(ds.path) != ds.md5:
            raise SystemExit(f"{ds.filename}: checksum differs from the registered version")
        raw = pd.read_csv(ds.path, low_memory=False, encoding_errors="replace")
        report = validate_raw(raw, ds.crop)
        report.update({"source": ds.url, "handle": ds.handle, "version": ds.dataverse_version, "md5": ds.md5})
        validation["datasets"][ds.key] = report
        validation["leakage_probes"][ds.key] = {
            col: round(float(raw[col].corr(raw["L-tonPerHectare"], method="spearman")), 3)
            for col in LEAKY_NUMERIC_PROBES if col in raw.columns
        }
        frames.append(tidy(raw, ds.crop, ds.key))

    tidy_all = pd.concat(frames, ignore_index=True)
    validation["cleaning"]["rows_before"] = int(len(tidy_all))
    cleaned, log = clean(tidy_all)
    validation["cleaning"]["rules"] = log
    validation["cleaning"]["rows_after"] = int(len(cleaned))
    validation["cleaning"]["rows_after_by_crop"] = {k: int(v) for k, v in cleaned["crop"].value_counts().items()}
    validation["cleaning"]["complex_grade_imputed_rows"] = int(cleaned["complex_grade_imputed"].sum())

    validation["features"] = {
        "missing_after_cleaning": {c: int(cleaned[c].isna().sum()) for c in FEATURES},
        "numeric_summary_by_crop": {
            crop: grp[NUMERIC_FEATURES + [TARGET]].describe(percentiles=[.01, .5, .99]).round(2).to_dict()
            for crop, grp in cleaned.groupby("crop")
        },
        "spearman_with_yield_by_crop": {
            crop: grp[NUMERIC_FEATURES].corrwith(grp[TARGET], method="spearman").round(3).to_dict()
            for crop, grp in cleaned.groupby("crop")
        },
        "districts": int(cleaned[["source", "district"]].drop_duplicates().shape[0]),
    }
    leaks = [f for f in FEATURES if f in LEAKAGE_EXCLUDED]
    if leaks:
        raise SystemExit(f"leakage guard: excluded fields used as features: {leaks}")

    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    cleaned.to_csv(PROCESSED_FILE, index=False)
    validation["processed_file"] = {"path": str(PROCESSED_FILE.relative_to(PROCESSED_DIR.parents[1])),
                                    "md5": md5_of(PROCESSED_FILE)}
    (REPORTS_DIR / "data_validation.json").write_text(json.dumps(validation, indent=2, default=str), encoding="utf-8")

    print(f"rows before cleaning: {validation['cleaning']['rows_before']}")
    for rule in log:
        print(f"  - {rule['rule']}: removed {rule['removed']} {rule['removed_by_crop']}")
    print(f"rows after cleaning: {len(cleaned)} {validation['cleaning']['rows_after_by_crop']}")
    print(f"wrote {PROCESSED_FILE} and {REPORTS_DIR / 'data_validation.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
