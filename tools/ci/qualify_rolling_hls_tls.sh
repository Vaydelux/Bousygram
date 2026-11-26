#!/usr/bin/env bash
# Host-only synthetic receiver qualification; ephemeral self-signed keys.
set -euo pipefail
umask 077
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fixtures="$(realpath "${1:?native fixture directory required}")"
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
mkdir "$scratch/certs"
certs="$scratch/certs"
for role in server client rogue; do
    keytool -genkeypair -alias peer -keyalg EC -groupname secp256r1 \
        -dname "CN=$role" -ext 'SAN=dns:localhost,ip:127.0.0.1' \
        -validity 2 -storetype PKCS12 -keystore "$certs/$role.p12" \
        -storepass qualification-only -keypass qualification-only -noprompt
    keytool -exportcert -alias peer -keystore "$certs/$role.p12" \
        -storepass qualification-only -file "$certs/$role.cer"
done
keytool -importcert -alias peer -file "$certs/client.cer" -storetype PKCS12 \
    -keystore "$certs/server-trust.p12" -storepass qualification-only -noprompt
keytool -importcert -alias peer -file "$certs/server.cer" -storetype PKCS12 \
    -keystore "$certs/client-trust.p12" -storepass qualification-only -noprompt
kotlin_bin="$(realpath "$(command -v kotlinc)")"
coroutines="$(dirname "$kotlin_bin")/../lib/kotlinx-coroutines-core-jvm.jar"
[[ -f "$coroutines" ]]
cast="$root/app/src/main/java/com/vaydelux/bousygram/core/cast"
debug="$root/app/src/debug/java/com/vaydelux/bousygram/core/cast"
kotlinc -classpath "$coroutines" \
    "$cast/RollingHlsBudget.kt" "$cast/RollingFmp4Framing.kt" \
    "$cast/RollingHlsSegmentStore.kt" "$cast/RollingHlsVerifiedFeed.kt" \
    "$cast/RollingFmp4SampleVerifier.kt" "$cast/RollingHlsStreamingSink.kt" \
    "$cast/RollingHlsDeliverySession.kt" "$cast/RollingHlsDeliveryDispatcher.kt" \
    "$debug/RollingHlsControlledQualification.kt" "$debug/RollingHlsPeerIdentity.kt" \
    "$root/app/src/main/java/com/vaydelux/bousygram/core/cache/TvStorageBudget.kt" \
    "$root/app/src/main/java/com/vaydelux/bousygram/core/account/AccountSession.kt" \
    "$root/tools/ci/fixtures/RollingHlsTlsSmoke.kt" -include-runtime -d "$scratch/replay.jar"
java -cp "$scratch/replay.jar:$coroutines" com.vaydelux.bousygram.core.cast.RollingHlsTlsSmokeKt \
    "$fixtures" "$scratch" "$certs"
