import os
import json
from datetime import datetime
import numpy as np
from sklearn.neural_network import MLPRegressor

# ─────────────────────────────────────────────
# EXACT IMPORTS FROM CLASSMATE'S FILES
# ─────────────────────────────────────────────
from part1_autoencoder import compute_features
from part2_deterministic import compute_raw_score, EMA_ALPHA, HR_FOCAL_THRESH, HR_FOCAL_WINDOWS
from part3_knn_detector import LatentKNNScorer

# ─────────────────────────────────────────────
# HELPER FUNCTIONS
# ─────────────────────────────────────────────
TS_FORMAT = '%Y-%m-%d %H:%M:%S'


# ─────────────────────────────────────────────
# LIVE WATCH DETECTOR
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

# Live State Memory to prevent amnesia
_live_history = []
_consecutive_alarms = 0
_last_ema_score = 0.0  # Tracks continuous momentum across all windows

def init_live_model(pretraining_dir):
    """
    Called ONCE by Kotlin when the watch connects.
    """
    global _latent_bank, _knn_scorer, _live_mlp, _live_scaler
    global _w_det, _w_ml, _thresh_warn, _thresh_alarm, _min_alarm_wins
    global _live_history, _consecutive_alarms, _last_ema_score

    # Reset memory on startup
    _live_history = []
    _consecutive_alarms = 0
    _last_ema_score = 0.0

    try:
        bank_path = os.path.join(pretraining_dir, "scn8a_latent_bank.f32")
        _latent_bank = np.fromfile(bank_path, dtype=np.float32).reshape(-1, 16)

        _knn_scorer = LatentKNNScorer().fit(_latent_bank)
        _knn_scorer.calibrate_threshold(_latent_bank)

        json_path = os.path.join(pretraining_dir, "scn8a_runtime_config.json")
        with open(json_path, 'r') as f:
            config = json.load(f)

        stage1 = config['stage1']

        # Extract dynamic thresholds (Now built for linear scores!)
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
    Called EVERY SECOND by Kotlin.
    """
    global _live_history, _consecutive_alarms, _last_ema_score

    if _latent_bank is None or _live_mlp is None:
        return "ERROR,0.000,0.000"

    try:
        accel_np = np.array(accel_array, dtype=np.float32)
        window_row = {
            'ts': datetime.now().strftime(TS_FORMAT),
            'hr': float(hr) if hr > 0 else 90.0,
            'accel_mg': accel_np
        }

        # 1. Update our sliding 10-window history (strictly for the focal_flag)
        _live_history.append(window_row)
        if len(_live_history) > 10:
            _live_history.pop(0)

        # 2. Get Deterministic Score (Now extremely lightweight and tracks true EMA)
        focal_flag = False
        if len(_live_history) >= HR_FOCAL_WINDOWS:
            hr_high = [r['hr'] >= HR_FOCAL_THRESH for r in _live_history[-HR_FOCAL_WINDOWS:]]
            if all(hr_high):
                focal_flag = True

        raw_det_score = compute_raw_score(window_row, focal_flag)

        # Apply the continuous EMA manually
        _last_ema_score = (EMA_ALPHA * raw_det_score) + ((1.0 - EMA_ALPHA) * _last_ema_score)
        deterministic_score = max(raw_det_score, _last_ema_score)

        # 3. Calculate ML Score (Only on the current window)
        feats = compute_features([window_row])[0]
        norm_feats = _live_scaler.transform([feats]).astype(np.float32)

        h = norm_feats
        for i in range(3):
            h = np.dot(h, _live_mlp.coefs_[i]) + _live_mlp.intercepts_[i]
            h = np.maximum(0, h)
        latent = h

        raw_score = _knn_scorer.score(latent.reshape(1, -1))
        ml_score = float(_knn_scorer.normalise_scores(raw_score)[0])

        # 4. THE TRUE DECISION LOGIC
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