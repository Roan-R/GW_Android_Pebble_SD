# ---------------------
# Stage 4: Supervised Calibrator (Lightweight Ensemble)
# ---------------------
#
# Uses seizure-log labels to find the optimal warning and alarm thresholds
# for the simple weighted ensemble calculated on the watch.
#
# Inputs come from:
#   Stage 2 -> deterministic seizure-rule scores
#   Stage 3 -> latent-space kNN anomaly score
#
# Output:
#   Optimal WARN / ALARM thresholds for the main pipeline.

import numpy as np
from dataclasses import dataclass

# ---------------------------------------------
# 1.  SETTINGS
# ---------------------------------------------

WARN_TARGET_RECALL  = 0.80
ALARM_TARGET_RECALL = 0.70
WARN_MAX_FALSE_POSITIVE_RATE  = 0.20
ALARM_MAX_FALSE_POSITIVE_RATE = 0.10

W_DETERMINISTIC = 0.50
W_ML            = 0.50

# ---------------------------------------------
# 2.  CALIBRATOR CONTAINER
# ---------------------------------------------

@dataclass
class SupervisedCalibrator:
    warn_threshold: float
    alarm_threshold: float
    w_det: float
    w_ml: float


# ---------------------------------------------
# 3.  FEATURE + LABEL BUILDERS
# ---------------------------------------------

def build_supervised_labels(rows: list) -> np.ndarray:
    """Binary labels from the seizure-log annotations added in main_pipeline."""
    return np.array([1 if r.get('log_is_seizure') else 0 for r in rows], dtype=np.int32)

def build_ensemble_scores(rows: list, w_det: float = W_DETERMINISTIC, w_ml: float = W_ML) -> np.ndarray:
    """Calculates the exact same linear ensemble score the watch will use live."""
    return np.array([
        (float(r.get('smooth_score', 0.0)) * w_det) + (float(r.get('ml_score', 0.0)) * w_ml)
        for r in rows
    ], dtype=np.float32)


# ---------------------------------------------
# 4.  THRESHOLD SELECTION
# ---------------------------------------------

def choose_threshold(scores: np.ndarray,
                     labels: np.ndarray,
                     target_recall: float,
                     max_false_positive_rate: float = None) -> float:
    """
    Pick the highest threshold that still achieves the target recall while
    minimising false positives on normal-labelled windows.
    """
    positive_total = int(labels.sum())
    negative_total = int((labels == 0).sum())
    if positive_total == 0:
        return 0.50

    candidates = np.unique(np.concatenate([
        np.linspace(0.0, 1.0, 201, dtype=np.float32),
        np.asarray(scores, dtype=np.float32),
    ]))

    feasible = []
    fallback = []
    for threshold in np.sort(candidates):
        preds = scores >= threshold
        tp = int(np.sum(preds & (labels == 1)))
        fp = int(np.sum(preds & (labels == 0)))
        recall = tp / positive_total if positive_total else 0.0
        fpr = fp / negative_total if negative_total else 0.0
        precision = tp / max(int(np.sum(preds)), 1)
        record = (fpr, -precision, -threshold, threshold, recall)

        if recall >= target_recall and (
            max_false_positive_rate is None or fpr <= max_false_positive_rate
        ):
            feasible.append(record)
        else:
            deficit = max(0.0, target_recall - recall)
            extra_fpr = max(
                0.0,
                0.0 if max_false_positive_rate is None else fpr - max_false_positive_rate,
            )
            fallback.append((deficit, extra_fpr, fpr, -precision, -threshold, threshold))

    if feasible:
        feasible.sort()
        return float(feasible[0][3])

    fallback.sort()
    return float(fallback[0][5]) if fallback else 0.50


def compute_threshold_metrics(scores: np.ndarray,
                              labels: np.ndarray,
                              threshold: float) -> dict:
    """Window-level recall / precision / false-positive-rate summary."""
    preds = scores >= threshold
    tp = int(np.sum(preds & (labels == 1)))
    fp = int(np.sum(preds & (labels == 0)))
    fn = int(np.sum((~preds) & (labels == 1)))
    tn = int(np.sum((~preds) & (labels == 0)))

    recall = tp / max(tp + fn, 1)
    precision = tp / max(tp + fp, 1)
    false_positive_rate = fp / max(fp + tn, 1)

    return {
        'threshold': float(threshold),
        'recall': float(recall),
        'precision': float(precision),
        'false_positive_rate': float(false_positive_rate),
        'tp': tp,
        'fp': fp,
        'fn': fn,
        'tn': tn,
    }


# ---------------------------------------------
# 5.  CALIBRATION
# ---------------------------------------------

def fit_supervised_calibrator(rows: list,
                              warn_target_recall: float = WARN_TARGET_RECALL,
                              alarm_target_recall: float = ALARM_TARGET_RECALL,
                              warn_max_false_positive_rate: float = WARN_MAX_FALSE_POSITIVE_RATE,
                              alarm_max_false_positive_rate: float = ALARM_MAX_FALSE_POSITIVE_RATE):
    """
    Find the optimal thresholds for the simple 50/50 linear split.
    Returns (calibrator, training_summary).
    """
    labels = build_supervised_labels(rows)
    train_scores = build_ensemble_scores(rows)

    warn_threshold = choose_threshold(
        train_scores,
        labels,
        target_recall=warn_target_recall,
        max_false_positive_rate=warn_max_false_positive_rate,
    )

    alarm_threshold = choose_threshold(
        train_scores,
        labels,
        target_recall=alarm_target_recall,
        max_false_positive_rate=alarm_max_false_positive_rate,
    )

    # Ensure warning is always lower than alarm
    if warn_threshold >= alarm_threshold:
        warn_threshold = max(0.0, min(alarm_threshold - 0.05, alarm_threshold * 0.8))

    calibrator = SupervisedCalibrator(
        warn_threshold=float(warn_threshold),
        alarm_threshold=float(alarm_threshold),
        w_det=W_DETERMINISTIC,
        w_ml=W_ML
    )

    summary = {
        'n_rows': int(len(rows)),
        'n_normal': int(np.sum(labels == 0)),
        'n_seizure': int(np.sum(labels == 1)),
        'warn_metrics': compute_threshold_metrics(train_scores, labels, calibrator.warn_threshold),
        'alarm_metrics': compute_threshold_metrics(train_scores, labels, calibrator.alarm_threshold),
    }

    return calibrator, summary