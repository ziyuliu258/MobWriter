#!/usr/bin/env bash
set -euo pipefail

# Package the generated artwork for Android without redrawing the mark.
# Requires ImageMagick 7 (magick).
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_image="$project_root/branding/mobwriter-logo-source.png"
resource_dir="$project_root/app/src/main/res/drawable-nodpi"
mkdir -p "$resource_dir"

# Ignore almost invisible alpha noise when measuring the artwork's bounds.
# Preserve the original alpha channel in all exported images.
artwork_bounds="$(magick "$source_image" -alpha extract -threshold 1% -format '%@' info:)"

# A 108 dp foreground at 4x density; the mark is 52 dp wide so its
# rounded outer corners remain within the centered 66 dp safe circle.
magick "$source_image" -crop "$artwork_bounds" +repage \
    -resize 208x208 -gravity center -background none -extent 432x432 \
    -strip "$resource_dir/mobwriter_launcher_foreground.png"

# The splash uses its own padding instead of the launcher's overscan margin.
magick "$source_image" -crop "$artwork_bounds" +repage \
    -resize 384x384 -gravity center -background none -extent 480x480 \
    -strip "$resource_dir/mobwriter_logo.png"

# A square, opaque preview of the launcher's visible 72 dp region.
magick "$source_image" -crop "$artwork_bounds" +repage \
    -resize 370x370 -gravity center -background none -extent 512x512 \
    -background '#F7F3EA' -alpha remove -alpha off \
    -strip "$project_root/branding/mobwriter-app-icon.png"
