#!/usr/bin/env bash
# Downloads Tabler Icons (MIT) used by Foreman into src/icons/.
# Run once before building: bash scripts/download-icons.sh

set -e

BASE="https://raw.githubusercontent.com/tabler/tabler-icons/refs/heads/main/icons/outline"
DEST="src/icons"

mkdir -p "$DEST"

icons=(
    folder-plus
    settings
    logout
    terminal-2
    focus-2
    notes
    info-circle
    keyboard
)

for icon in "${icons[@]}"; do
    echo "Downloading $icon.svg..."
    curl -sSfL "$BASE/$icon.svg" -o "$DEST/$icon.svg"
done

echo "Done — ${#icons[@]} icons saved to $DEST/"
