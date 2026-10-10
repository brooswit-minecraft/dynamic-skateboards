"""Regression contract for retrying a partial first release.

The retry must read the already-created GitHub release and download its exact
jar before retrying Modrinth.  It must neither rebuild nor replace that release.
"""
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).parents[1] / ".github/workflows/release.yml"


class ReleaseRetryWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.workflow = WORKFLOW.read_text(encoding="utf-8")

    def section(self, start, end):
        return self.workflow.split(start, 1)[1].split(end, 1)[0]

    def test_manual_retry_authenticates_and_reuses_immutable_artifact(self):
        compute = self.section("      - name: Compute version", "      - uses: actions/setup-java")
        self.assertIn("GH_TOKEN: ${{ github.token }}", compute)
        self.assertIn("elif [ \"${{ github.event_name }}\" = workflow_dispatch ]", compute)
        self.assertIn('echo "new=false"', compute)
        self.assertIn('gh release view "$last_tag" --json body --jq .body', compute)

        build = self.section("      - name: Build and test", "      - name: Publish immutable GitHub release")
        publish = self.section("      - name: Publish immutable GitHub release", "      - name: Fetch release artifact")
        fetch = self.section("      - name: Fetch release artifact", "      - uses: oven-sh/setup-bun")
        self.assertIn("if: steps.version.outputs.new == 'true'", build)
        self.assertIn("if: steps.version.outputs.new == 'true'", publish)
        self.assertIn("if: steps.version.outputs.value", fetch)
        self.assertIn("GH_TOKEN: ${{ github.token }}", fetch)
        self.assertIn('gh release download "v$VERSION" --pattern "dynamicskateboards-$VERSION.jar"', fetch)


if __name__ == "__main__":
    unittest.main()
