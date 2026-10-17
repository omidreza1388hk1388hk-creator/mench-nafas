#!/usr/bin/env sh
# Downloads the four Vazirmatn weights (SIL OFL) into the app's assets/fonts folder.
# Run from the project root (Termux works):  sh scripts/fetch-fonts.sh
set -e
DEST="android/app/src/main/assets/fonts"
BASE="https://github.com/rastikerdar/vazirmatn/raw/master/fonts/ttf"
mkdir -p "$DEST"
for w in Regular Medium SemiBold Bold; do
  echo "Downloading Vazirmatn-$w.ttf ..."
  curl -L --fail -o "$DEST/Vazirmatn-$w.ttf" "$BASE/Vazirmatn-$w.ttf"
done
echo "Done. Rebuild the app; Type.kt picks the files up automatically."
