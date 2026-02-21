# Flake Triage Policy

Use this flow whenever a cross-runtime stress job fails.

1. Reproduce locally with the exact matrix command from CI.
2. Isolate the introducing change:
   - narrow to one failing scenario/direction,
   - bisect if needed.
3. Fix the root cause (do not mask with retries-only changes).
4. Add or tighten a deterministic test to guard the failure mode.
5. Re-run targeted stress and then full interop stress before merge.

If immediate fix is not possible, open an issue with:
- failing matrix entry,
- reproduction command,
- first known bad commit/range,
- attached logs and test reports.
