"""
test_cnn_svm.py
===============
Unit tests for the CNN + SVM jackfruit disease classifier defined in
``cnn_svm_model.py``.

All tests use small synthetic images so they run instantly on any machine
without a real dataset or GPU.  Each test class targets one stage of the
pipeline; the final class (``TestEndToEnd``) trains the full model and
prints the evaluation results so you can read accuracy, the classification
report, and the confusion matrix directly from the test output.

Run with:
    python -m pytest test_cnn_svm.py -v
or:
    python test_cnn_svm.py
"""

from __future__ import annotations

import unittest
import numpy as np

from cnn_svm_model import (
    LABELS,
    IMAGE_SIZE,
    FILTER_BANK,
    preprocess_image,
    extract_features,
    extract_features_batch,
    build_svm_pipeline,
    train,
    evaluate,
    run_pipeline,
)

# ---------------------------------------------------------------------------
# Helpers shared across test cases
# ---------------------------------------------------------------------------

RNG = np.random.default_rng(seed=0)

def _random_image(h: int = 80, w: int = 80, colour: bool = False) -> np.ndarray:
    """Create a random uint8 image."""
    if colour:
        return RNG.integers(0, 256, size=(h, w, 3), dtype=np.uint8)
    return RNG.integers(0, 256, size=(h, w), dtype=np.uint8)


def _make_synthetic_dataset(n_per_class: int = 8,
                              img_h: int = 64, img_w: int = 64) -> tuple:
    """
    Return ``(raw_images, labels)`` with *n_per_class* images per disease label.

    Diseased images have progressively brighter pixel values so the SVM has a
    signal to learn from even with random data.
    """
    images: list[np.ndarray] = []
    labels: list[str]        = []

    brightness_map = {
        "Healthy_Jackfruit": 50,
        "R10":               100,
        "R25":               150,
        "R50":               200,
        "R100":              220,
    }

    for label in LABELS:
        base = brightness_map[label]
        for _ in range(n_per_class):
            img = RNG.integers(
                max(0, base - 30), min(255, base + 30),
                size=(img_h, img_w), dtype=np.uint8,
            )
            images.append(img)
            labels.append(label)

    return images, labels


# ============================================================================
# 1. Image pre-processing
# ============================================================================

class TestPreprocessImage(unittest.TestCase):

    def test_output_shape_matches_target_size(self):
        """Processed image must have the configured (H, W) dimensions."""
        img = _random_image(120, 90)
        out = preprocess_image(img)
        self.assertEqual(out.shape, IMAGE_SIZE)

    def test_output_dtype_is_float32(self):
        """Processed image must be float32."""
        img = _random_image()
        out = preprocess_image(img)
        self.assertEqual(out.dtype, np.float32)

    def test_output_values_in_unit_range(self):
        """All pixel values must lie in [0, 1]."""
        img = _random_image()
        out = preprocess_image(img)
        self.assertGreaterEqual(float(out.min()), 0.0)
        self.assertLessEqual(float(out.max()), 1.0)

    def test_colour_image_is_converted_to_grayscale(self):
        """A colour (H, W, 3) input must produce a 2-D output."""
        img = _random_image(colour=True)
        out = preprocess_image(img)
        self.assertEqual(out.ndim, 2)

    def test_already_grayscale_input_is_accepted(self):
        """A (H, W) input must not raise an error."""
        img = _random_image(colour=False)
        out = preprocess_image(img)
        self.assertEqual(out.ndim, 2)

    def test_custom_size_is_respected(self):
        """Passing a non-default size must resize the image accordingly."""
        img = _random_image()
        out = preprocess_image(img, size=(32, 48))
        self.assertEqual(out.shape, (32, 48))


# ============================================================================
# 2. Filter bank
# ============================================================================

class TestFilterBank(unittest.TestCase):

    def test_filter_bank_is_non_empty(self):
        self.assertGreater(len(FILTER_BANK), 0)

    def test_all_filters_are_3x3(self):
        for f in FILTER_BANK:
            self.assertEqual(f.shape, (3, 3),
                             msg=f"Filter shape {f.shape} is not (3, 3)")

    def test_all_filters_are_float32(self):
        for f in FILTER_BANK:
            self.assertEqual(f.dtype, np.float32)


# ============================================================================
# 3. CNN feature extraction (single image)
# ============================================================================

class TestExtractFeatures(unittest.TestCase):

    def setUp(self):
        raw = _random_image()
        self.proc = preprocess_image(raw)
        self.features = extract_features(self.proc)

    def test_features_are_1d(self):
        """Feature vector must be 1-D."""
        self.assertEqual(self.features.ndim, 1)

    def test_features_are_float32(self):
        self.assertEqual(self.features.dtype, np.float32)

    def test_features_are_non_empty(self):
        self.assertGreater(len(self.features), 0)

    def test_features_are_finite(self):
        """No NaN or Inf values must appear in the feature vector."""
        self.assertTrue(np.all(np.isfinite(self.features)))

    def test_feature_length_is_consistent(self):
        """Two images of the same size must produce vectors of the same length."""
        raw2 = _random_image()
        proc2 = preprocess_image(raw2)
        feat2 = extract_features(proc2)
        self.assertEqual(len(self.features), len(feat2))

    def test_features_are_non_negative_after_relu(self):
        """ReLU activation means all pooled feature values must be ≥ 0."""
        self.assertGreaterEqual(float(self.features.min()), 0.0)

    def test_different_images_produce_different_features(self):
        """Distinct input images must yield distinct feature vectors."""
        bright = np.ones(IMAGE_SIZE, dtype=np.float32)
        dark   = np.zeros(IMAGE_SIZE, dtype=np.float32)
        self.assertFalse(np.array_equal(extract_features(bright),
                                        extract_features(dark)))


# ============================================================================
# 4. Batch feature extraction
# ============================================================================

class TestExtractFeaturesBatch(unittest.TestCase):

    def test_output_shape_rows_match_input_count(self):
        images = [preprocess_image(_random_image()) for _ in range(5)]
        X = extract_features_batch(images)
        self.assertEqual(X.shape[0], 5)

    def test_output_shape_cols_match_single_extraction(self):
        images = [preprocess_image(_random_image()) for _ in range(3)]
        X = extract_features_batch(images)
        n_features = len(extract_features(images[0]))
        self.assertEqual(X.shape[1], n_features)

    def test_single_image_batch(self):
        """A batch of one image must still produce a 2-D matrix."""
        img = preprocess_image(_random_image())
        X = extract_features_batch([img])
        self.assertEqual(X.ndim, 2)
        self.assertEqual(X.shape[0], 1)


# ============================================================================
# 5. SVM pipeline construction
# ============================================================================

class TestBuildSvmPipeline(unittest.TestCase):

    def test_pipeline_has_scaler_step(self):
        pipe = build_svm_pipeline()
        self.assertIn("scaler", pipe.named_steps)

    def test_pipeline_has_svm_step(self):
        pipe = build_svm_pipeline()
        self.assertIn("svm", pipe.named_steps)

    def test_custom_C_is_passed_through(self):
        pipe = build_svm_pipeline(C=5.0)
        self.assertEqual(pipe.named_steps["svm"].C, 5.0)


# ============================================================================
# 6. SVM training
# ============================================================================

class TestTrainSVM(unittest.TestCase):

    def setUp(self):
        raw_images, labels = _make_synthetic_dataset(n_per_class=6)
        processed = [preprocess_image(img) for img in raw_images]
        self.X = extract_features_batch(processed)
        self.y = np.array(labels)

    def test_train_returns_pipeline(self):
        from sklearn.pipeline import Pipeline
        pipeline = train(self.X, self.y)
        self.assertIsInstance(pipeline, Pipeline)

    def test_pipeline_is_fitted_after_training(self):
        """The fitted pipeline should expose 'classes_' on its SVM step."""
        pipeline = train(self.X, self.y)
        self.assertTrue(hasattr(pipeline.named_steps["svm"], "classes_"))

    def test_svm_classes_contain_all_labels(self):
        pipeline = train(self.X, self.y)
        for label in LABELS:
            self.assertIn(label, pipeline.named_steps["svm"].classes_)

    def test_predict_returns_array_of_correct_length(self):
        pipeline = train(self.X, self.y)
        preds = pipeline.predict(self.X)
        self.assertEqual(len(preds), len(self.y))

    def test_predict_output_only_contains_known_labels(self):
        pipeline = train(self.X, self.y)
        preds = pipeline.predict(self.X)
        for p in preds:
            self.assertIn(p, LABELS)


# ============================================================================
# 7. Evaluation helper
# ============================================================================

class TestEvaluate(unittest.TestCase):

    def setUp(self):
        raw, labels = _make_synthetic_dataset(n_per_class=8)
        processed = [preprocess_image(img) for img in raw]
        X = extract_features_batch(processed)
        y = np.array(labels)
        self.pipeline = train(X, y)
        self.X_test = X
        self.y_test = y

    def test_returns_dict_with_required_keys(self):
        results = evaluate(self.pipeline, self.X_test, self.y_test)
        for key in ("accuracy", "report", "confusion_matrix", "predictions"):
            self.assertIn(key, results, msg=f"Missing key: {key}")

    def test_accuracy_is_between_0_and_1(self):
        results = evaluate(self.pipeline, self.X_test, self.y_test)
        self.assertGreaterEqual(results["accuracy"], 0.0)
        self.assertLessEqual(results["accuracy"], 1.0)

    def test_confusion_matrix_shape(self):
        results = evaluate(self.pipeline, self.X_test, self.y_test)
        cm = results["confusion_matrix"]
        self.assertEqual(cm.shape, (len(LABELS), len(LABELS)))

    def test_confusion_matrix_row_sums_equal_class_counts(self):
        """Each row of the confusion matrix must sum to the number of true samples."""
        results = evaluate(self.pipeline, self.X_test, self.y_test)
        cm = results["confusion_matrix"]
        n_per_class = len(self.y_test) // len(LABELS)
        for i, row_sum in enumerate(cm.sum(axis=1)):
            self.assertEqual(row_sum, n_per_class,
                             msg=f"Row {i} ({LABELS[i]}) sum = {row_sum}")

    def test_predictions_length_matches_test_set(self):
        results = evaluate(self.pipeline, self.X_test, self.y_test)
        self.assertEqual(len(results["predictions"]), len(self.y_test))

    def test_report_contains_all_label_names(self):
        results = evaluate(self.pipeline, self.X_test, self.y_test)
        for label in LABELS:
            self.assertIn(label, results["report"])


# ============================================================================
# 8. End-to-end pipeline (train → predict → evaluate) — results are printed
# ============================================================================

class TestEndToEnd(unittest.TestCase):
    """
    Full pipeline test: synthetic data → preprocess → extract → train SVM →
    evaluate.  The evaluation block is printed to stdout so you can inspect
    accuracy, the classification report, and the confusion matrix.
    """

    N_TRAIN = 12   # samples per class for training
    N_TEST  = 4    # samples per class for testing

    @classmethod
    def setUpClass(cls):
        """Build train / test splits once for the whole class."""
        train_raw, train_labels = _make_synthetic_dataset(n_per_class=cls.N_TRAIN)
        test_raw,  test_labels  = _make_synthetic_dataset(n_per_class=cls.N_TEST)
        cls.results = run_pipeline(
            train_raw, train_labels,
            test_raw,  test_labels,
        )

    # --- Structural checks ---

    def test_pipeline_result_has_all_keys(self):
        for key in ("accuracy", "report", "confusion_matrix", "predictions"):
            self.assertIn(key, self.results)

    def test_accuracy_is_valid_float(self):
        acc = self.results["accuracy"]
        self.assertIsInstance(acc, float)
        self.assertGreaterEqual(acc, 0.0)
        self.assertLessEqual(acc, 1.0)

    def test_confusion_matrix_is_square_with_correct_size(self):
        cm = self.results["confusion_matrix"]
        self.assertEqual(cm.shape, (len(LABELS), len(LABELS)))

    def test_predictions_count_matches_test_set_size(self):
        expected = self.N_TEST * len(LABELS)
        self.assertEqual(len(self.results["predictions"]), expected)

    def test_all_predictions_are_known_labels(self):
        for p in self.results["predictions"]:
            self.assertIn(p, LABELS)

    def test_report_string_contains_accuracy_line(self):
        self.assertIn("accuracy", self.results["report"])

    # --- Quality check (dataset is structured so this should succeed) ---

    def test_accuracy_above_chance(self):
        """With class-specific brightness, the SVM should beat random (20%)."""
        self.assertGreater(self.results["accuracy"], 1.0 / len(LABELS))


# ---------------------------------------------------------------------------
# Allow running with:  python test_cnn_svm.py
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    unittest.main(verbosity=2)
