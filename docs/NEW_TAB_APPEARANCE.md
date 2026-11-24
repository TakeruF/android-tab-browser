# New-tab appearance

Open the palette on a new tab, or Settings → Appearance → New tab background.
Changes apply immediately; Done closes the editor. A live preview shows the
chosen background behind a search field, using the same readable surface as
the new-tab page.

Default, Solid color, Gradient and Photo are arranged as two-column options
with small background samples, a selected border and a check. Solid color and
Gradient share their saved color. The palette offers preset swatches; the
current HEX value and pencil open the custom color editor. Switching modes
retains the saved color and photo.

Photo opens Android's image picker if no photo is saved. Selecting it from
another mode reuses the saved photo. Selecting the already active Photo option
opens the picker to replace it; cancellation preserves the current mode, photo
and stored file. The Photo option exposes the localized Choose photo action to
accessibility services. Importing disables mode and color changes and shows
progress. Private browsing may reuse an available saved photo but cannot
import one.

The editor uses the shared UI-density dimensions. Its preview has a bounded
height, labels can grow for large text, and the content scrolls in short
windows. Mode names, color labels and photo guidance use the six existing
localizations.

`NewTabAppearanceTest` checks preset-to-preview color updates, shared color
between Solid color and Gradient, custom editing, Settings access and Activity
recreation. Its photo case supplies controlled results through the real picker
contract, checks accessible replacement/cancellation/reuse, and verifies the
bounded EXIF-oriented copy after deleting the source. The controlled picker
does not establish a particular cloud-photo provider's behavior.

The 2026-11-24 local run passed both cases at 1000 dp/light and 411 dp/dark.
Screenshots were inspected; physical-device and cloud-photo-provider behavior
remain separate checks. See [validation](VALIDATION.md#compact-preference-and-background-editor-2026-11-24-local).
