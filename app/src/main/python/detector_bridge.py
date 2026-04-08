import numpy as np
from part2_deterministic import compute_jerk, get_seizure_type, STD_ALARM_MG
from part1_autoencoder import extract_features

# Global storage for stateful detection (like focal seizure 6-window count)
history = {"focal_count": 0}

def detect_seizure_window(hr, accel_125):
    """
    Called by Kotlin every 1 second (or per window).
    hr: float (BPM)
    accel_125: list of 125 integers (milli-g)
    """
    accel_array = np.array(accel_125)

    # 1. Basic Stats (Stage 2 Logic)
    std_val = np.std(accel_array)
    jmn, jmx = compute_jerk(accel_array)

    # 2. Logic Check
    # You can call the exact functions from your classmate's part2_deterministic.py
    # to see if the current window triggers a 'tonic-clonic', 'focal', etc.
    sz_type = get_seizure_type(std_val, jmx, jmn, hr, foc=False)

    if std_val > STD_ALARM_MG or (hr > 110 and std_val > 50):
        return f"ALARM: {sz_type}"

    return "OK"