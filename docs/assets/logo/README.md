# foreman logo

**Mark** — stacked terminal panes, the front one live and prompting ("one view at a time").
Drawn on the Tabler 24px grid: 2px stroke, round caps and joins.

| File | Use |
|---|---|
| `logo-mark.svg` | The glyph alone. Strokes are `currentColor` — set `color` (or inherit) so it works on dark and light. The prompt cursor stays brand green `#3FA463`. |
| `logo-tile.svg` | App / menu-bar icon: 128px rounded tile (`#3F4244`, 22% radius, hairline `#595C5E`) with the mark at `#DDDDDD`. |
| `logo-wordmark.svg` | Horizontal lockup: tile + **foreman** (monospace, lowercase) + tagline *TERMINALS BY PROJECT*. |

## Derived assets

| File | Use |
|---|---|
| `logo-wordmark-light.svg` | The lockup with dark text, for light backgrounds (GitHub light-mode README). |
| `social-preview.png` | 1280×640 card for the repo's social preview (uploaded manually: Settings → General). |
| `../../../cmd/foreman-explorer/icon-template.png` | Menu bar template icon: the mark black-on-transparent at 64px, embedded in fm-explorer. |

Rasters are rendered from the SVGs with headless Chrome (ImageMagick's SVG renderer drops the
anti-aliasing), e.g. for the menu bar icon: wrap the mark (all strokes `#000`) in an HTML page
at the target size and run
`"Google Chrome" --headless=new --screenshot=out.png --window-size=64,64 --default-background-color=00000000 page.html`.

## Rules
- Green is only the cursor. Never recolor the whole mark green, never add a second hue.
- Lowercase `foreman` always, monospace always.
- Clear space around the mark = the width of one pane corner radius (~2.6 units on the 24 grid).
- Minimum size 16px. Below that use the mark only, never the lockup.
- No gradients, no shadows, no filled/duotone variants.
