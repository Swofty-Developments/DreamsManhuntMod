#!/usr/bin/env bash
# Builds the release assets into build/release/:
#   dreammanhunt-<v>.jar, dreammanhunt-<v>-sources.jar,
#   DreamManhunt-<v>-server.zip (Fabric server with the mod and Fabric API),
#   DreamManhunt-<v>-prism.zip (Prism Launcher / MultiMC instance with the mod and Fabric API),
#   SHA256SUMS.txt
# Usage: scripts/package-release.sh [version]   (defaults to the version in gradle.properties)
set -euo pipefail
cd "$(dirname "$0")/.."

prop() { grep "^$1=" gradle.properties | cut -d= -f2-; }
VERSION="${1:-$(prop version)}"
MC="$(prop minecraft_version)"
LOADER="$(prop loader_version)"
FAPI="$(prop fabric_api_version)"
INSTALLER="$(prop installer_version)"

./gradlew --no-configuration-cache build -Pversion="$VERSION"

OUT=build/release
STAGE=build/release-stage
rm -rf "$OUT" "$STAGE"
mkdir -p "$OUT" "$STAGE"

MOD_JAR="dreammanhunt-$VERSION.jar"
cp "build/libs/$MOD_JAR" "$OUT/"
cp "build/libs/dreammanhunt-$VERSION-sources.jar" "$OUT/"

FAPI_JAR="fabric-api-$FAPI.jar"
curl -fsSL -o "$STAGE/$FAPI_JAR" \
	"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FAPI//+/%2B}/fabric-api-${FAPI//+/%2B}.jar"
curl -fsSL -o "$STAGE/fabric-server-launch.jar" \
	"https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar"

SERVER="$STAGE/server"
mkdir -p "$SERVER/mods"
cp "$STAGE/fabric-server-launch.jar" "$SERVER/"
cp "$OUT/$MOD_JAR" "$STAGE/$FAPI_JAR" "$SERVER/mods/"
cat > "$SERVER/start.sh" <<'EOF'
#!/usr/bin/env bash
cd "$(dirname "$0")"
exec java -Xms2G -Xmx6G -jar fabric-server-launch.jar nogui
EOF
chmod +x "$SERVER/start.sh"
printf '@echo off\r\ncd /d "%%~dp0"\r\njava -Xms2G -Xmx6G -jar fabric-server-launch.jar nogui\r\npause\r\n' > "$SERVER/start.bat"
cat > "$SERVER/README.txt" <<EOF
DreamManhunt $VERSION server (Minecraft $MC, Fabric Loader $LOADER, Fabric API $FAPI)

1. Install Java 25.
2. Run start.sh (Linux/macOS) or start.bat (Windows) once.
3. Accept the Minecraft EULA in eula.txt and run it again.
4. Op yourself (op <name> in the console) to save and load savestates.

Every player needs the DreamManhunt client: import DreamManhunt-$VERSION-prism.zip into Prism Launcher,
or put $MOD_JAR and $FAPI_JAR in a Fabric $LOADER client's mods folder.
EOF

PRISM="$STAGE/prism"
mkdir -p "$PRISM/.minecraft/mods"
cp "$OUT/$MOD_JAR" "$STAGE/$FAPI_JAR" "$PRISM/.minecraft/mods/"
cat > "$PRISM/instance.cfg" <<EOF
[General]
ConfigVersion=1.3
InstanceType=OneSix
name=DreamManhunt $VERSION
iconKey=default
EOF
cat > "$PRISM/mmc-pack.json" <<EOF
{
    "components": [
        {
            "uid": "net.minecraft",
            "version": "$MC",
            "important": true
        },
        {
            "uid": "net.fabricmc.intermediary",
            "version": "$MC",
            "dependencyOnly": true
        },
        {
            "uid": "net.fabricmc.fabric-loader",
            "version": "$LOADER"
        }
    ],
    "formatVersion": 1
}
EOF

python3 - "$STAGE" "$OUT" "$VERSION" <<'EOF'
import os, sys, zipfile
stage, out, version = sys.argv[1:]
def pack(src, dest, prefix):
    with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(src):
            for name in sorted(files):
                path = os.path.join(root, name)
                arc = os.path.join(prefix, os.path.relpath(path, src)) if prefix else os.path.relpath(path, src)
                info = zipfile.ZipInfo.from_file(path, arc)
                info.external_attr = (0o755 if name.endswith(".sh") else 0o644) << 16
                with open(path, "rb") as f:
                    z.writestr(info, f.read(), zipfile.ZIP_DEFLATED)
pack(os.path.join(stage, "server"), os.path.join(out, f"DreamManhunt-{version}-server.zip"), f"DreamManhunt-{version}-server")
pack(os.path.join(stage, "prism"), os.path.join(out, f"DreamManhunt-{version}-prism.zip"), "")
EOF

(cd "$OUT" && sha256sum -- * > SHA256SUMS.txt)
rm -rf "$STAGE"
ls -l "$OUT"
