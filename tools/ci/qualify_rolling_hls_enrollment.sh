#!/usr/bin/env bash
# Host-only ephemeral TLS enrollment regression. No private application data.
set -euo pipefail
umask 077
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
for role in tv sender rogue; do
    keytool -genkeypair -alias peer -keyalg EC -groupname secp256r1 \
        -dname "CN=$role" -validity 2 -storetype PKCS12 -keystore "$scratch/$role.p12" \
        -storepass qualification-only -keypass qualification-only -noprompt
done
kotlin_bin="$(realpath "$(command -v kotlinc)")"
coroutines="$(dirname "$kotlin_bin")/../lib/kotlinx-coroutines-core-jvm.jar"
[[ -f "$coroutines" ]]
debug="$root/app/src/debug/java/com/vaydelux/bousygram/core/cast"
kotlinc -classpath "$coroutines" "$debug/RollingHlsEnrollment.kt" \
    "$debug/RollingHlsPeerIdentity.kt" "$debug/RollingHlsPeerPairing.kt" \
    "$root/app/src/main/java/com/vaydelux/bousygram/core/account/AccountSession.kt" \
    "$root/tools/ci/fixtures/RollingHlsEnrollmentSmoke.kt" -include-runtime -d "$scratch/enrollment.jar"
java -cp "$scratch/enrollment.jar:$coroutines" com.vaydelux.bousygram.core.cast.RollingHlsEnrollmentSmokeKt "$scratch"
kotlinc -classpath "$coroutines" "$debug/RollingHlsEnrollment.kt" \
    "$debug/RollingHlsPeerIdentity.kt" "$debug/RollingHlsPeerPairing.kt" "$debug/RollingHlsPairingQr.kt" \
    "$debug/RollingHlsLanInvitation.kt" "$debug/RollingHlsLanProbe.kt" \
    "$root/app/src/main/java/com/vaydelux/bousygram/core/account/AccountSession.kt" \
    "$root/tools/ci/fixtures/RollingHlsLanSmoke.kt" -include-runtime -d "$scratch/lan.jar"
java -cp "$scratch/lan.jar:$coroutines" com.vaydelux.bousygram.core.cast.RollingHlsLanSmokeKt "$scratch"
