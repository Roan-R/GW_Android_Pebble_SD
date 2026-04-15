import os
import glob
import csv
import json
from datetime import datetime
import numpy as np
from sklearn.neural_network import MLPRegressor

# ─────────────────────────────────────────────
# EXACT IMPORTS FROM CLASSMATE'S FILES
# ─────────────────────────────────────────────
from part1_autoencoder import (
    load_all_csvs, impute_hr, compute_features, 
    normalize_features
)
from part2_deterministic import run_pipeline
from part3_knn_detector import LatentKNNScorer, run_ml_inference


# ─────────────────────────────────────────────
# HELPER FUNCTIONS
# ─────────────────────────────────────────────
TS_FORMAT = '%Y-%m-%d %H:%M:%S'

def parse_timestamp(ts: str) -> datetime:
    return datetime.strptime(ts.strip(), TS_FORMAT)

def load_seizure_log(filepath: str) -> list:
    seizure_times = []
    with open(filepath, newline='') as f:
        reader = csv.reader(f)
        for row in reader:
            if not row: continue
            raw_ts = row[0].strip()
            if not raw_ts or raw_ts.startswith('#'): continue
            try:
                seizure_times.append(parse_timestamp(raw_ts))
            except ValueError:
                continue
    seizure_times.sort()
    return seizure_times


# ─────────────────────────────────────────────
# PART B: LIVE WATCH DETECTOR
# ─────────────────────────────────────────────

# Global memory for live inference
_latent_bank = None
_knn_scorer = None
_live_mlp = None
_live_scaler = None

# Dynamic thresholds from the JSON
_w_det = 0.5
_w_ml = 0.5
_thresh_warn = 0.05
_thresh_alarm = 0.613
_min_alarm_wins = 4

# NEW: Live State Memory to prevent amnesia
_live_history = []
_consecutive_alarms = 0

def init_live_model(pretraining_dir):
    """
    Called ONCE by Kotlin when the watch connects.
    """
    global _latent_bank, _knn_scorer, _live_mlp, _live_scaler
    global _w_det, _w_ml, _thresh_warn, _thresh_alarm, _min_alarm_wins
    global _live_history, _consecutive_alarms

    # Reset memory on startup
    _live_history = []
    _consecutive_alarms = 0

    try:
        bank_path = os.path.join(pretraining_dir, "scn8a_latent_bank.f32")
        _latent_bank = np.fromfile(bank_path, dtype=np.float32).reshape(-1, 16)

        _knn_scorer = LatentKNNScorer().fit(_latent_bank)
        _knn_scorer.calibrate_threshold(_latent_bank)

        json_path = os.path.join(pretraining_dir, "scn8a_runtime_config.json")
        with open(json_path, 'r') as f:
            config = json.load(f)

        stage1 = config['stage1']

        # Extract dynamic thresholds
        ensemble = config.get('stage3', {}).get('ensemble_weights', {})
        _w_det = ensemble.get('deterministic', 0.5)
        _w_ml  = ensemble.get('ml', 0.5)

        stage4 = config.get('stage4', {})
        _thresh_warn  = stage4.get('warn_threshold', 0.05)
        _thresh_alarm = stage4.get('alarm_threshold', 0.613)

        # Extract the required consecutive windows
        policy = config.get('runtime_policy', {}).get('main_constants', {})
        _min_alarm_wins = policy.get('STAGE4_MIN_ALARM_WINDOWS', 4)

        _live_mlp = MLPRegressor(hidden_layer_sizes=(64, 32, 16), max_iter=1)
        _live_mlp.coefs_ = [np.array(w) for w in stage1['encoder_weights']]
        _live_mlp.intercepts_ = [np.array(b) for b in stage1['encoder_biases']]

        from sklearn.preprocessing import StandardScaler
        _live_scaler = StandardScaler()
        _live_scaler.mean_ = np.array(stage1['scaler_mean'])
        _live_scaler.scale_ = np.array(stage1['scaler_scale'])
        _live_scaler.var_ = _live_scaler.scale_ ** 2

        return "SUCCESS: Live model primed and ready."
    except Exception as e:
        import traceback
        return f"ERROR loading model: {traceback.format_exc()}"


def detect_live_window(hr, accel_array):
    """
    Called EVERY SECOND by Kotlin or the Simulator.
    """
    global _live_history, _consecutive_alarms

    if _latent_bank is None or _live_mlp is None:
        return "ERROR,0.000,0.000"

    try:
        accel_np = np.array(accel_array, dtype=np.float32)
        window_row = {
            'ts': datetime.now().strftime(TS_FORMAT),
            'hr': float(hr) if hr > 0 else 90.0,
            'accel_mg': accel_np
        }

        # 1. Update our sliding 10-window history
        _live_history.append(window_row)
        if len(_live_history) > 10:
            _live_history.pop(0)

        # 2. Get Deterministic Score (Now properly uses history for smoothing & focal seizures!)
        processed = run_pipeline(_live_history)
        deterministic_score = processed[-1].get('smooth_score', 0.0)

        # 3. Calculate ML Score (Only on the current window)
        feats = compute_features([window_row])[0]
        norm_feats = _live_scaler.transform([feats]).astype(np.float32)

        h = norm_feats
        for i in range(3):
            h = np.dot(h, _live_mlp.coefs_[i]) + _live_mlp.intercepts_[i]
            h = np.maximum(0, h)
        latent = h

        raw_score = _knn_scorer.score(latent)
        ml_score = float(_knn_scorer.normalise_scores(raw_score)[0])

        # 4. THE TRUE DECISION LOGIC (The Ensemble State Machine)
        ensemble_score = (deterministic_score * _w_det) + (ml_score * _w_ml)

        # Track consecutive high scores
        if ensemble_score >= _thresh_alarm:
            _consecutive_alarms += 1
        else:
            _consecutive_alarms = 0

        # Only trigger ALARM if sustained for the required windows
        if _consecutive_alarms >= _min_alarm_wins:
            status = "ALARM"
        elif ensemble_score >= _thresh_warn:
            status = "WARNING"
        else:
            status = "OK"

        return f"{status},{ensemble_score:.3f},{ml_score:.3f}"

    except Exception as e:
        import traceback
        print(f"Python Error: {traceback.format_exc()}")
        return "ERROR,0.000,0.000"


def simulate_watch_from_csv(internal_data_dir, pretraining_dir):
    """
    Simulates the live Garmin watch by feeding an extended CSV dataset
    through the pretrained ML model one window at a time.
    """
    try:
        # 1. Prime the Brain with the .f32 and .json files
        init_res = init_live_model(pretraining_dir)
        if "ERROR" in init_res:
            return init_res

        # 2. Load the Extended Dataset
        csv_files = sorted(glob.glob(os.path.join(internal_data_dir, "*.csv")))
        if not csv_files:
            return f"Error: No CSV files found in {internal_data_dir}"

        all_rows = load_all_csvs(csv_files)
        all_rows = impute_hr(all_rows)

        alarms = 0
        warnings = 0

        # 3. Simulate the Watch (Feed data 1 second at a time)
        for row in all_rows:
            hr = row.get('hr', 90.0)

            if 'accel_mg' in row:
                accel = row['accel_mg']
            else:
                accel = np.zeros(125, dtype=np.float32)

            # Pass to the Live Detector (e.g. returns "ALARM,0.650,0.500")
            raw_status = detect_live_window(hr, accel)

            # Split it so the simulator only looks at the word!
            status = raw_status.split(",")[0]

            if status == "ALARM":
                alarms += 1
            elif status == "WARNING":
                warnings += 1

        return (f"Simulation Complete!\n"
                f"Files Processed: {len(csv_files)}\n"
                f"Total Windows: {len(all_rows)}\n"
                f"Warnings Triggered: {warnings}\n"
                f"Alarms Triggered: {alarms}")

    except Exception as e:
        import traceback
        return f"Simulation Error:\n{traceback.format_exc()}"