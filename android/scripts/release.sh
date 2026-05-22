#!/usr/bin/env bash
# Riz release helper. One-shot: bump version → build APKs → sign +
# upload manifest → git commit/tag. Confirms before each write operation.
#
#   ./scripts/release.sh                # bump minor (1.0.0 → 1.1.0) + release
#   ./scripts/release.sh 1.0.1          # explicit target version
#   ./scripts/release.sh --bump patch   # 1.0.0 → 1.0.1
#   ./scripts/release.sh --dry-run      # print the plan, no writes
#   ./scripts/release.sh --skip-build   # reuse the on-disk APKs
#   ./scripts/release.sh --skip-bump    # republish current version (no version change, no git)
#   ./scripts/release.sh --skip-git     # bump + publish but don't auto-commit/tag
#   ./scripts/release.sh -y             # non-interactive (CI)
#   ./scripts/release.sh --mandatory    # mark forced upgrade in manifest
#
# Versions:
#   versionCode bumps by +1 every release (Android requires monotonic).
#   versionName is semver X.Y.Z; default policy is `--bump minor` (1.0.0 → 1.1.0).
#
# The wrapper forwards all arguments verbatim to `oiyoa-publish release`.
# Drive file IDs, signing key, OAuth refresh token live in
# oiyoa-android-libs/.drive_ids.txt + publish-tools/.env — see
# publish-tools/README.md.

set -euo pipefail
cd "$(dirname "$0")/.."

LIBS="$(realpath ../../oiyoa/oiyoa-android-libs)"

exec uv run --directory "$LIBS/publish-tools" --frozen oiyoa-publish release \
    --repo-dir "$PWD" \
    --app-id "com.riz.app" \
    --app-name "Riz" \
    "$@"
