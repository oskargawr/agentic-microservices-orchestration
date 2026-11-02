#!/usr/bin/env bash
# TH-206: fail the CI job if ConsoleRealTemporalE2EIT was missing, skipped, or failed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
XML="$ROOT/orchestration/console/build/test-results/test/TEST-pl.edu.pw.thesis.orchestration.console.ConsoleRealTemporalE2EIT.xml"

if [[ ! -f "$XML" ]]; then
  echo "ERROR: missing JUnit report for ConsoleRealTemporalE2EIT: $XML"
  echo "The Temporal E2E test did not run (disabled/skipped/not executed)."
  exit 1
fi

# Extract attributes from the root testsuite element
attrs="$(head -n 5 "$XML" | tr '\n' ' ')"
echo "ConsoleRealTemporalE2EIT report: $attrs"

echo "$attrs" | grep -Eq 'tests="([1-9][0-9]*)"' || {
  echo "ERROR: expected tests>=1 in $XML"
  exit 1
}
echo "$attrs" | grep -Eq 'skipped="0"' || {
  echo "ERROR: ConsoleRealTemporalE2EIT was skipped (skipped!=0). Silent skip is forbidden in CI."
  exit 1
}
echo "$attrs" | grep -Eq 'failures="0"' || {
  echo "ERROR: ConsoleRealTemporalE2EIT had failures"
  exit 1
}
echo "$attrs" | grep -Eq 'errors="0"' || {
  echo "ERROR: ConsoleRealTemporalE2EIT had errors"
  exit 1
}

echo "OK: ConsoleRealTemporalE2EIT executed with skipped=0"
