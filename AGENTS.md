# Agent Rules

## Branch naming (required for correct release notes)

Pull request labels are assigned by `.github/labeler.yml` (run via
`.github/workflows/label-pull-requests.yml`) based on the **head branch name**.
Those labels drive the categorised release notes produced by
`gh release create --generate-notes` (configured in `.github/release.yml`).

A branch that does not start with a recognised prefix gets **no label** and its
changes land under **"Others 🧃"** in the changelog.

Name every branch with one of these prefixes, chosen to match the change:

| Branch prefix | Label | Release notes section |
| --- | --- | --- |
| `feature/…` | `feature` | New Features 🚀 |
| `fix/…`, `hotfix/…` | `fix` | Bugfixes 🪲 |
| `chore/…`, `docs/…`, `documentation/…`, `ci/…`, `refactor/…` | `chore` | Documentation & CI 🪂 |
| anything else | *(none)* | Others 🧃 |

Rules:

- Always start the branch name with one of the prefixes above, e.g.
  `feature/add-download-endpoint`, `fix/token-expiry`, `chore/bump-jackson`.
- Do **not** invent new prefixes such as `build/`, `test/`, or `deps/`: they are
  not in `.github/labeler.yml`, so the PR receives no label and the release notes
  are wrong. Put build, dependency, and tooling work under `chore/`.
- Set the branch name **before opening the PR**. The labeler only runs on
  `pull_request: [opened, edited]`, so a branch renamed afterwards can keep stale
  labels.
- To keep a change out of the release notes entirely, apply the
  `ignore-for-release` label.

`.github/labeler.yml` is the source of truth for labels and `.github/release.yml`
for categories — keep this file in sync when either changes.
