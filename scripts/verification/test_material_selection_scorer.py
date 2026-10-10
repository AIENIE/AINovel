import importlib.util
import json
from pathlib import Path
import unittest

path = Path(__file__).with_name("score-material-selection.py")
spec = importlib.util.spec_from_file_location("scorer", path)
scorer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scorer)

class MaterialSelectionScorerTest(unittest.TestCase):
    def fixture(self):
        manifest = scorer.load(scorer.DATA / "manifest.json")
        answers = scorer.load(scorer.DATA / "development-answers.json")
        # Oracle rankings test the scorer only. They must never be reported as model outputs.
        return {"configuration": manifest["configuration"], "datasetSha256": manifest["sha256"], "rows": [
            {"queryId": answer["id"], "arm": arm, "chunks": list(answer["relevance"]), "elapsedMs": 1}
            for arm in manifest["configuration"]["arms"] for answer in answers
        ]}

    def test_oracle_scoring_does_not_approve_development_results(self):
        report = scorer.score(self.fixture(), "development")
        self.assertEqual(120, report["queries"])
        self.assertEqual(1, report["metrics"]["basic"]["evidenceRecallAt8"])
        self.assertEqual(1, report["metrics"]["basic"]["nDCGAt8"])
        self.assertEqual("basic", report["engineeringRecommendation"]["embedding"])

    def test_permission_leak_is_counted_even_below_the_display_cutoff(self):
        fixture = self.fixture()
        corpus = scorer.load(scorer.DATA / "corpus.json")
        allowed = [chunk for source in corpus if source["visibility"] == "CURRENT" for chunk, _ in scorer.fragments(source)]
        forbidden = next(source["id"] + ":0" for source in corpus if source["visibility"] == "OTHER_OWNER")
        fixture["rows"][0]["chunks"] = allowed[:8] + [forbidden]
        self.assertEqual(1, scorer.score(fixture, "development")["metrics"]["basic"]["permissionOrVersionLeaks"])

    def test_missing_queries_and_changed_configuration_fail_closed(self):
        fixture = self.fixture()
        fixture["rows"] = []
        report = scorer.score(fixture, "development")
        self.assertEqual(120, report["metrics"]["flash"]["missingQueries"])
        self.assertEqual(0, report["metrics"]["flash"]["failedQueries"])
        self.assertEqual(0, report["metrics"]["flash"]["executedQueries"])
        self.assertIsNone(report["metrics"]["flash"]["failureRate"])
        self.assertIsNone(report["metrics"]["flash"]["evidenceRecallAt8"])
        self.assertEqual("basic", report["engineeringRecommendation"]["embedding"])
        fixture["configuration"]["dimensions"] = 2048
        with self.assertRaises(ValueError):
            scorer.score(fixture, "development")

if __name__ == "__main__":
    unittest.main()
