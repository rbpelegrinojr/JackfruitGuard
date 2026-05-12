"""
cnn_svm_model.py
================
CNN feature extractor + SVM classifier for jackfruit disease severity.

Architecture
------------
1. **CNN Feature Extractor** (pure NumPy / SciPy)
   - Three convolutional stages, each with a bank of learned-style filters
     (edge detectors, Gabor-like, Laplacian) followed by ReLU activation and
     average pooling.
   - The feature maps from all stages are flattened and concatenated into a
     single 1-D feature vector per image.

2. **SVM Classifier** (scikit-learn)
   - Radial-basis-function (RBF) kernel SVM trained on the extracted features.
   - Class labels: Healthy_Jackfruit | R10 | R25 | R50 | R100

The module is intentionally free of TensorFlow / PyTorch so that all unit
tests run on the local JVM / plain Python environment without a GPU.
"""

from __future__ import annotations

import numpy as np
from scipy.ndimage import convolve
from sklearn.svm import SVC
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
)
from sklearn.pipeline import Pipeline

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

LABELS = ["Healthy_Jackfruit", "R10", "R25", "R50", "R100"]
IMAGE_SIZE = (64, 64)          # (height, width) – images are resized here
POOL_SIZE   = 4                # average-pool window (square)
RANDOM_SEED = 42

# ---------------------------------------------------------------------------
# Filter banks
# ---------------------------------------------------------------------------

def _make_filter_bank() -> list[np.ndarray]:
    """Return a small bank of 3×3 convolution kernels (edge / texture detectors)."""
    sobel_x = np.array([[-1, 0, 1],
                         [-2, 0, 2],
                         [-1, 0, 1]], dtype=np.float32)

    sobel_y = sobel_x.T.copy()

    laplacian = np.array([[0,  1, 0],
                           [1, -4, 1],
                           [0,  1, 0]], dtype=np.float32)

    sharpen  = np.array([[ 0, -1,  0],
                          [-1,  5, -1],
                          [ 0, -1,  0]], dtype=np.float32)

    emboss   = np.array([[-2, -1, 0],
                          [-1,  1, 1],
                          [ 0,  1, 2]], dtype=np.float32)

    identity = np.eye(3, dtype=np.float32)

    return [sobel_x, sobel_y, laplacian, sharpen, emboss, identity]


FILTER_BANK: list[np.ndarray] = _make_filter_bank()

# ---------------------------------------------------------------------------
# Image pre-processing
# ---------------------------------------------------------------------------

def preprocess_image(image: np.ndarray, size: tuple[int, int] = IMAGE_SIZE) -> np.ndarray:
    """
    Resize and normalise a single image to *size* (H, W).

    Parameters
    ----------
    image : np.ndarray
        Input image. Shape ``(H, W)`` (grayscale) or ``(H, W, C)`` (colour).
    size  : tuple[int, int]
        Target ``(height, width)``.

    Returns
    -------
    np.ndarray
        Grayscale image of shape ``size``, dtype float32, values in [0, 1].
    """
    from PIL import Image as PILImage

    if image.ndim == 3:
        pil = PILImage.fromarray(image.astype(np.uint8))
        pil = pil.convert("L")          # → grayscale
    else:
        pil = PILImage.fromarray(image.astype(np.uint8), mode="L")

    pil = pil.resize((size[1], size[0]), PILImage.BILINEAR)
    return np.array(pil, dtype=np.float32) / 255.0


# ---------------------------------------------------------------------------
# CNN-like feature extraction
# ---------------------------------------------------------------------------

def _relu(x: np.ndarray) -> np.ndarray:
    return np.maximum(0.0, x)


def _average_pool(feature_map: np.ndarray, pool_size: int = POOL_SIZE) -> np.ndarray:
    """
    Non-overlapping average pooling on a 2-D feature map.
    Output shape: ``(H // pool_size, W // pool_size)``.
    """
    h, w = feature_map.shape
    h_out = h // pool_size
    w_out = w // pool_size
    out = feature_map[: h_out * pool_size, : w_out * pool_size]
    out = out.reshape(h_out, pool_size, w_out, pool_size)
    return out.mean(axis=(1, 3))


def _convolve_stage(image: np.ndarray, filters: list[np.ndarray]) -> np.ndarray:
    """
    Apply a filter bank to *image*, ReLU, then average-pool.

    Returns
    -------
    np.ndarray  Concatenated, flattened feature vector for this stage.
    """
    stage_features: list[np.ndarray] = []
    for f in filters:
        conv = convolve(image, f, mode="reflect")
        activated = _relu(conv)
        pooled = _average_pool(activated)
        stage_features.append(pooled.ravel())
    return np.concatenate(stage_features)


def extract_features(image: np.ndarray) -> np.ndarray:
    """
    Run the three-stage CNN feature extractor on a single pre-processed image.

    Stage 1: full-resolution convolution + pool → 16×16 maps per filter
    Stage 2: operate on stage-1 mean map (down-sampled input)
    Stage 3: operate on stage-2 mean map

    Parameters
    ----------
    image : np.ndarray
        Grayscale image, shape ``(H, W)``, values in [0, 1].

    Returns
    -------
    np.ndarray
        1-D feature vector (dtype float32).
    """
    # Stage 1 — original resolution
    s1_features = _convolve_stage(image, FILTER_BANK)

    # Build a "down-sampled" input for stage 2 by pooling the image itself
    stage2_input = _average_pool(image, pool_size=POOL_SIZE)
    s2_features  = _convolve_stage(stage2_input, FILTER_BANK)

    # Stage 3 — further down-sampled
    stage3_input = _average_pool(stage2_input, pool_size=POOL_SIZE)
    if min(stage3_input.shape) >= 3:           # only if map is still large enough
        s3_features = _convolve_stage(stage3_input, FILTER_BANK)
    else:
        s3_features = np.array([], dtype=np.float32)

    return np.concatenate([s1_features, s2_features, s3_features]).astype(np.float32)


def extract_features_batch(images: list[np.ndarray]) -> np.ndarray:
    """
    Extract CNN features for a list of pre-processed images.

    Returns
    -------
    np.ndarray  Shape ``(n_samples, n_features)``.
    """
    return np.vstack([extract_features(img) for img in images])


# ---------------------------------------------------------------------------
# SVM classifier pipeline
# ---------------------------------------------------------------------------

def build_svm_pipeline(C: float = 10.0, gamma: str = "scale") -> Pipeline:
    """
    Return a scikit-learn Pipeline: StandardScaler → RBF SVC.

    Parameters
    ----------
    C     : float  Regularisation parameter for SVM.
    gamma : str    Kernel coefficient (``'scale'`` or ``'auto'`` or float).
    """
    return Pipeline([
        ("scaler", StandardScaler()),
        ("svm",    SVC(C=C, kernel="rbf", gamma=gamma, probability=True,
                       random_state=RANDOM_SEED)),
    ])


def train(X_train: np.ndarray, y_train: np.ndarray,
          C: float = 10.0, gamma: str = "scale") -> Pipeline:
    """
    Fit the SVM pipeline on pre-extracted feature vectors.

    Parameters
    ----------
    X_train : np.ndarray  Shape ``(n_samples, n_features)``.
    y_train : np.ndarray  1-D array of string labels.

    Returns
    -------
    Pipeline  Fitted ``build_svm_pipeline`` instance.
    """
    pipeline = build_svm_pipeline(C=C, gamma=gamma)
    pipeline.fit(X_train, y_train)
    return pipeline


def evaluate(pipeline: Pipeline,
             X_test: np.ndarray,
             y_test: np.ndarray) -> dict:
    """
    Evaluate the fitted pipeline and print a human-readable report.

    Returns
    -------
    dict with keys:
        ``accuracy``         : float
        ``report``           : str  (scikit-learn classification_report)
        ``confusion_matrix`` : np.ndarray
        ``predictions``      : np.ndarray
    """
    y_pred = pipeline.predict(X_test)
    acc    = accuracy_score(y_test, y_pred)
    report = classification_report(y_test, y_pred, zero_division=0)
    cm     = confusion_matrix(y_test, y_pred, labels=LABELS)

    print("=" * 60)
    print("CNN + SVM  –  Evaluation Results")
    print("=" * 60)
    print(f"Accuracy : {acc * 100:.2f}%\n")
    print("Classification Report:")
    print(report)
    print("Confusion Matrix (rows=actual, cols=predicted):")
    print("Labels:", LABELS)
    print(cm)
    print("=" * 60)

    return {
        "accuracy":          acc,
        "report":            report,
        "confusion_matrix":  cm,
        "predictions":       y_pred,
    }


# ---------------------------------------------------------------------------
# End-to-end convenience function
# ---------------------------------------------------------------------------

def run_pipeline(raw_train_images: list[np.ndarray],
                 train_labels: list[str],
                 raw_test_images:  list[np.ndarray],
                 test_labels:  list[str],
                 C: float = 10.0,
                 gamma: str = "scale") -> dict:
    """
    Full pipeline: preprocess → extract features → train SVM → evaluate.

    Parameters
    ----------
    raw_train_images : list of np.ndarray  Raw (unprocessed) training images.
    train_labels     : list of str          Corresponding disease labels.
    raw_test_images  : list of np.ndarray  Raw test images.
    test_labels      : list of str          Corresponding disease labels.

    Returns
    -------
    dict  Same structure as :func:`evaluate`.
    """
    print("Preprocessing images ...")
    train_proc = [preprocess_image(img) for img in raw_train_images]
    test_proc  = [preprocess_image(img) for img in raw_test_images]

    print("Extracting CNN features ...")
    X_train = extract_features_batch(train_proc)
    X_test  = extract_features_batch(test_proc)

    print("Training SVM classifier ...")
    pipeline = train(X_train, np.array(train_labels), C=C, gamma=gamma)

    print("Evaluating ...")
    return evaluate(pipeline, X_test, np.array(test_labels))
