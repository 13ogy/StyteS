#!/bin/bash
# run-all-demos.sh
#
# Regression harness for the StyteS pub/sub project.
# Runs the full JUnit suite + every centralised demo with `-ea`,
# then summarises pass/fail. Distributed demos (those extending
# AbstractDistributedCVM) require GlobalRegistry + DCVMCyclicBarrier
# and are skipped here — see docs/SOUTENANCE.md §2d for the
# Demo3JVMs walkthrough.
#
# Usage: bash run-all-demos.sh [JDK_HOME]
#   JDK_HOME — optional path to a JDK (default: corretto-19 if found).
#
# Author: Bogdan Styn, Setbel Mélissa

set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

JDK_HOME="${1:-/Users/bogy/Library/Java/JavaVirtualMachines/corretto-19.0.2/Contents/Home}"
if [ -d "$JDK_HOME/bin" ]; then
  export PATH="$JDK_HOME/bin:$PATH"
fi

echo "==> JDK"
java -version
echo

# 1. Compile
echo "==> Compiling"
find src -name '*.java' > /tmp/stytes-sources.txt
# Compile to /tmp to escape iCloud Drive's mid-run "filename 2" duplications
# (Desktop is iCloud-synced and class files vanish from `out/` during runs).
OUT=/tmp/stytes-out
rm -rf "$OUT" && mkdir -p "$OUT"
if ! javac -d "$OUT" -cp 'libs/*' @/tmp/stytes-sources.txt 2>&1 | tee /tmp/stytes-compile.log | tail -3; then
  echo "FATAL: compile failed (see /tmp/stytes-compile.log)"
  exit 1
fi

NB_SRC=$(wc -l < /tmp/stytes-sources.txt)
echo "compiled $NB_SRC sources"
echo

# 2. JUnit
echo "==> JUnit (under -ea)"
TESTS=(
  fr.sorbonne_u.cps.pubsub.tests.MessageTest
  fr.sorbonne_u.cps.pubsub.tests.MessageFilterTest
  fr.sorbonne_u.cps.pubsub.tests.PropertyFilterTest
  fr.sorbonne_u.cps.pubsub.tests.ComparableValueFilterTest
  fr.sorbonne_u.cps.pubsub.tests.Position2DTest
  fr.sorbonne_u.cps.pubsub.tests.MeteoMessageFactoryTest
  fr.sorbonne_u.cps.pubsub.tests.BrokerRegistrationTest
  fr.sorbonne_u.cps.pubsub.tests.GossipMessageTest
  fr.sorbonne_u.cps.pubsub.tests.GossipMessageVisitorTest
)
java -ea -cp "$OUT:libs/*" org.junit.runner.JUnitCore "${TESTS[@]}" 2>&1 \
  | tee /tmp/stytes-junit.log | tail -3
JUNIT_RC=${PIPESTATUS[0]}
echo

# 3. Centralised demos
echo "==> Centralised demos (under -ea, 130 s timeout each)"
DEMOS=(
  DemoMidTermDemoV2
  DemoPluginsClient
  DemoSeparatedPlugin
  DemoMidSemComplexTimedScenario
  DemoMeteoAudit1
  DemoMeteoTimedTestTool
  DemoAudit2SecondVersion
  DemoAudit2TimedScenario
)
mkdir -p /tmp/stytes-demos
TOTAL=0
PASSED=0
for D in "${DEMOS[@]}"; do
  TOTAL=$((TOTAL+1))
  LOG="/tmp/stytes-demos/${D}.log"
  timeout 130 java -ea -cp "$OUT:libs/*" "fr.sorbonne_u.cps.pubsub.demo.${D}" > "$LOG" 2>&1
  RC=$?
  ERRS=$(grep -cE 'AssertionError|Exception in thread|java\.lang\.[A-Za-z]*Error|Caused by' "$LOG")
  LAST=$(tail -1 "$LOG")
  if [ "$RC" -eq 0 ] && [ "$ERRS" -eq 0 ] && [ "$LAST" = "ending..." ]; then
    PASSED=$((PASSED+1))
    echo "  PASS $D"
  else
    echo "  FAIL $D (rc=$RC errs=$ERRS last=\"$LAST\")"
  fi
done

echo
echo "================================================================"
echo "JUnit:           rc=$JUNIT_RC  (see /tmp/stytes-junit.log)"
echo "Central demos:   $PASSED/$TOTAL passed (logs in /tmp/stytes-demos/)"
echo "Distributed:     not executed — see docs/SOUTENANCE.md §2d"
echo "================================================================"

if [ "$JUNIT_RC" -eq 0 ] && [ "$PASSED" -eq "$TOTAL" ]; then
  echo "GREEN"
  exit 0
else
  echo "RED — investigate logs above"
  exit 1
fi
