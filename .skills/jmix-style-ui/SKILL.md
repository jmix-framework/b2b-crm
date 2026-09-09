---
name: jmix-style-ui
description: Style Jmix Flow UI components — views, fragments, grid renderers, or components built in a controller. Read this before writing any getStyle().set(...), classNames, addThemeVariants, or CSS custom property (--aura-* / --lumo-* / --vaadin-*). A custom property the active theme does not define fails SILENTLY: no error, every gate green, wrong rendering.
---

# Style Flow UI components

Applies to any component you style: view and fragment layouts, grid renderers,
badges, cards, and components built in a controller.

## Step 0 — identify the ACTIVE theme before typing a token

Theme tokens are CSS custom properties, and each theme defines its OWN set. A
token from the wrong theme is undefined, and an undefined custom property does
not error — the browser silently falls back. So find the theme first:

```bash
# the main application class declares the theme stylesheets
grep -rn "@StyleSheet\|@Theme" src/main/java --include='*.java'
```

- `@StyleSheet(Aura.STYLESHEET)` + `@StyleSheet(JmixAura.STYLESHEET)` → **Aura**,
  tokens are `--aura-*` (Jmix 3's newer default).
- A Lumo theme declaration (or neither of the above) → **Lumo**, tokens are
  `--lumo-*`.

The project's own CSS usually sits next to those declarations, e.g.
`@StyleSheet("themes/<app>-aura/styles.css")` →
`src/main/resources/META-INF/resources/themes/<app>-aura/styles.css`, or a
`src/main/frontend/themes/<name>/` folder. That file is where your own rules go.

**Never mix the families.** `--lumo-primary-text-color` in an Aura app resolves to
nothing.

## Where the styling belongs — in this order

**1. A CSS rule in the project theme + `classNames` — preferred.** Reusable,
inspectable, and it keeps values out of Java:

```css
/* src/main/resources/META-INF/resources/themes/<app>/<view>.css */
.order-card {
    border: 1px solid var(--aura-accent-border-color);
    border-radius: var(--aura-base-radius);
}
```

```xml
<vbox id="orderBox" classNames="order-card"/>
```

```java
card.addClassName("order-card");   // when the component is built in the controller
```

A new CSS file is not loaded by existing — wire it in: add
`@import url('<view>.css');` to the theme's `styles.css` (the stylesheet the
application declares). A file nothing imports fails exactly like an undefined
token: no error anywhere, the rules just never apply.

**2. A built-in component theme variant — for looks the component already has.**
Do not re-implement a badge or a size variant with hand-written CSS:

```java
avatar.addThemeVariants(AvatarVariant.LARGE);
badge.getElement().getThemeList().addAll(List.of("badge", "pill"));   // badge theme: Vaadin's under Lumo, Jmix's under Aura
```

`ThemeList` holds single tokens — `add("badge pill")` happens to render (the
attribute joins entries with spaces) but stores one bogus entry, so a later
`remove("pill")` or `contains("badge")` silently does nothing.

**A variant name is theme-specific, exactly like a token.** A `*Variant` constant
is only a string the theme has to style: `ButtonVariant.LUMO_TERTIARY_INLINE`
sets `theme="tertiary-inline"`, Aura has no rule for that name, and the call
therefore does nothing — the button keeps its default background, border, and
shadow. One enum holds all the families — `LUMO_*`, `AURA_*`, and theme-neutral
constants (`PRIMARY`, `TERTIARY`, `SUCCESS`, `WARNING`, `ERROR`, `SMALL`,
`LARGE`) — and they all compile the same. Prefer the theme-neutral constant, and
confirm the active theme styles the name it maps to:

**Grep every stylesheet the app declares, not just the Vaadin one.** A Jmix app
loads `Aura.STYLESHEET` *and* `JmixAura.STYLESHEET`, and each layer brings its own
`[theme~=...]` rules, so a name missing from one may be styled by the other. The
`badge` and `pill` above are exactly that case: they come from the Jmix layer, and
a grep of the Vaadin jar alone reports them as unstyled.

```bash
# the cache usually holds several versions — list them and set the two variables
# to the ones matching the project's Vaadin and Jmix versions
find ~/.gradle/caches \( -name 'vaadin-aura-theme-*.jar' \
  -o -name 'jmix-flowui-themes-*.jar' \) ! -name '*-sources.jar'
JAR=...     # vaadin-aura-theme-<vaadin version>.jar
JMIX=...    # jmix-flowui-themes-<jmix version>.jar

# 1. the Vaadin layer (Aura.STYLESHEET) — one file, values unquoted
unzip -p "$JAR" 'META-INF/resources/aura/aura.css' \
  | grep -ohE "theme~=['\"]?[a-z0-9-]+" | sed -E "s/theme~=['\"]?//" | sort -u

# 2. the Jmix layer (JmixAura.STYLESHEET) — ~120 files, values quoted.
# Extract, then grep: a member glob is not portable, because on Windows unzip a
# '*' does not cross '/', so 'META-INF/resources/themes/jmix-aura/*.css' matches
# only the top-level file and prints nothing. Same reason the whole jar comes
# out, not just the theme folder — it is under 1 MB.
DIR=$(mktemp -d)
unzip -oq "$JMIX" -d "$DIR"
grep -rhoE "theme~=['\"]?[a-z0-9-]+" "$DIR/META-INF/resources/themes/jmix-aura" \
  | sed -E "s/theme~=['\"]?//" | sort -u
```

Under Lumo the same two commands read `META-INF/resources/lumo/lumo.css` in
`vaadin-lumo-theme-*.jar` and the extracted `themes/jmix-lumo` folder from the
same Jmix jar. If grep reports the folder does not exist, check the version you
picked — Jmix 2.x ships `jmix-lumo` only. A third place a name can be styled is
the project's own theme CSS — grep that folder too.

Across both layers (Aura 25.1/25.2 with Jmix 3.0/3.1) `primary` and `tertiary`
are styled, but `tertiary-inline` appears in neither — a name a Lumo-era call site
is likely to carry.

**Read the selector, not just the name.** A hit does not mean the variant works on
your component. In the Jmix layer `contrast` exists only as
`[theme~='badge'][theme~='contrast']` and `icon` only on one specific menu bar, so
on a plain button both still do nothing. (`icon-button` is a different name, not a
match for `icon`.)

**3. Inline `getStyle().set(...)` — last resort.** Correct for a one-off dynamic
value (a color computed from data, a width from a measurement), not for a look
that repeats across components:

```java
statusLabel.getStyle().set("color", "var(--aura-accent-text-color)");
```

## Tokens that exist — enumerate them, do not invent them

Verify a token the way you verify an API symbol: it must appear in the active
theme's stylesheet. The full list is one command away, so there is no reason to
guess:

```bash
# after a build, the theme is in node_modules
grep -rhoE '\-\-aura-[a-z0-9-]+' node_modules/@vaadin/aura/ | sort -u
grep -rhoE '\-\-lumo-[a-z0-9-]+' node_modules/@vaadin/vaadin-lumo-styles/ | sort -u

# no node_modules? read it from the resolved jar
JAR=$(find ~/.gradle/caches -name 'vaadin-aura-theme-*.jar' | grep -v sources | tail -1)
unzip -p "$JAR" 'META-INF/resources/aura/aura.css' | grep -ohE '\-\-aura-[a-z0-9-]+' | sort -u

# the same stylesheet also defines the shared --vaadin-* layer — enumerate it too
unzip -p "$JAR" 'META-INF/resources/aura/aura.css' | grep -ohE '\-\-vaadin-[a-z0-9-]+' | sort -u
```

Aura's set is small — under a hundred names, so the enumeration above is quick to
read in full. The ones you reach for most:

| Purpose | Aura token |
|---|---|
| accent / link text | `--aura-accent-text-color` |
| accent fill | `--aura-accent-color` |
| text ON an accent fill | `--aura-accent-contrast-color` |
| border | `--aura-accent-border-color` |
| panel / card background | `--aura-surface-color`, `--aura-surface-color-solid` |
| page background | `--aura-background-color`, `--aura-app-background` |
| corner radius | `--aura-base-radius` |
| font size | `--aura-font-size-xs` … `-xl` |
| font weight | `--aura-font-weight-regular` / `-medium` / `-semibold` |
| line height | `--aura-line-height-xs` … `-xl` |
| shadow | `--aura-shadow-xs` / `-s` / `-m` |
| status colors | `--aura-red`, `--aura-green`, `--aura-yellow`, `--aura-orange`, `--aura-blue`, `--aura-purple` (each with a `-text` variant, e.g. `--aura-red-text`), `--aura-neutral` (no `-text` variant) |

**The same stylesheet defines a second, bigger family: `--vaadin-*`.** These are
the semantic tokens of Vaadin's base styles (~230 names in Aura 25.2, ~280 in
Lumo 25.2), and for common needs they are the first place to look — Aura in
particular has no secondary-text token of its own, but it does define
`--vaadin-text-color-secondary`. The namespace is shared — a `--vaadin-*` name
means the same thing under any theme — but each theme sets values only for the
subset it restyles (just 82 names are common to Aura and Lumo in 25.2), so a
name being theme-agnostic does not make it universally defined. Verify a
`--vaadin-*` name in the active theme's stylesheet exactly like an
`--aura-*`/`--lumo-*` one (e.g. `--vaadin-gap-*` / `--vaadin-radius-*` exist in
Aura but not in Lumo). This core is defined by BOTH themes in 25.2:

| Purpose | `--vaadin-*` token (in both Aura and Lumo) |
|---|---|
| body text | `--vaadin-text-color` |
| muted / secondary text | `--vaadin-text-color-secondary` |
| disabled text | `--vaadin-text-color-disabled` |
| plain border | `--vaadin-border-color`, `--vaadin-border-color-secondary` |
| background | `--vaadin-background-color`, `--vaadin-background-container`, `--vaadin-background-container-strong` |
| focus ring | `--vaadin-focus-ring-color`, `--vaadin-focus-ring-width` |
| per-component knobs | `--vaadin-button-padding`, `--vaadin-card-padding`, … (set ON the component to retune it) |

**The two themes are not name-for-name equivalents.** Do not translate a Lumo
token into an `--aura-` prefix and assume it exists. Concretely: Lumo has
`--lumo-body-text-color`, `--lumo-secondary-text-color`, and a contrast scale
(`--lumo-contrast-20pct` …); **Aura has none of those.** In Aura, body text is
`--vaadin-text-color`, muted text is `--vaadin-text-color-secondary`, and a
plain border is `--vaadin-border-color` — the theme-neutral layer above, not
the `--aura-*` family. Going the other way, Lumo has no `--lumo-accent-surface`
or `--lumo-surface-level`.

## When the active theme has no suitable token

Three acceptable options, in this order:

1. **A `--vaadin-*` token from the base-styles layer** — it usually has
   what the theme's own family lacks; enumerate and verify it the same way.
2. **Define your own custom property or class in the project theme stylesheet**
   and use it — the value lives in one place and survives a theme change.
3. **Use a literal, theme-neutral value** for a genuinely neutral decoration:

```java
card.getStyle()
        .set("border", "1px solid rgba(128, 128, 128, 0.3)")
        .set("border-radius", "6px");
```

Borrowing a token from the other theme is never an option — the next section is
why it cannot be caught by any gate. When you
rely on a token for something essential (a link's affordance, a status color),
add a theme-independent fallback too, e.g. `text-decoration: underline` next to
the accent color.

## Why a wrong token is invisible to every gate

An undefined CSS custom property is not an error anywhere in the stack. The
declaration parses, the build succeeds, and the browser applies the fallback:

| Property | Falls back to |
|---|---|
| `color` | the inherited color (often near-black) |
| `background-color` | transparent |
| `border-color` | `currentColor` |
| `border-radius` | `0` |
| any length | the property's initial value |

So the defect survives `compileJava`, the Jmix inspection, and a green
`clean test`. The rendering is merely wrong — no exception, no warning, nothing in
the log.

**CSS tokens do not resolve automatically in drawing APIs.** Some chart,
diagram, or other components paint to a `<canvas>` and pass colour options
directly to a JavaScript drawing API. Such APIs do not interpret `var(...)` as
CSS. Before passing a theme token to a component option, check whether the
component resolves CSS custom properties itself. If it does not, use the
component's palette/theme API, a literal colour, or resolve the custom
property first (for example with
`getComputedStyle(...).getPropertyValue(...)`) and pass the resulting colour
value.

## Verify — the COMPUTED style, in a browser

The declaration looks identical whether the property resolves or not, so reading
the Java or the CSS proves nothing. Open the view with a browser tool (Gate 3 in
`jmix-verify-bootrun`) and read the computed value:

```js
getComputedStyle(document.querySelector('#statusLabel')).color
```

A resolved token gives a real color (`rgb(...)`); an undefined one gives the
inherited value — that difference is the check. Do the same for
`borderRadius`/`borderColor` when you set them. If no browser tool is available,
say `styling not browser-verified` rather than calling it done.

This check applies only when the value becomes a CSS declaration. If a
component passes the value directly to a drawing API, there may be no
corresponding computed style to inspect; verify the rendered result and, when
necessary, inspect the component's frontend implementation to confirm whether it
resolves CSS custom properties.

The same check catches a dead theme variant, because a variant the theme does not
style computes exactly like the component with no variant at all:

```js
getComputedStyle(document.querySelector('#orderButton')).backgroundColor
```

Under Aura a working `tertiary` gives `rgba(0, 0, 0, 0)`; a name the theme never
styles gives the default button fill. Compare against a plain component of the
same type — if the two match, the variant did nothing.

## Forbidden

- `--lumo-*` tokens in an Aura app, or `--aura-*` in a Lumo app.
- A `LUMO_*` theme variant in an Aura app (or `AURA_*` in a Lumo app) whose
  variant string the active theme does not style — it compiles and does nothing.
- A guessed token name — confirm it in the active theme's stylesheet first.
- Passing `var(...)` to a component option consumed directly by a drawing API
  unless the component explicitly resolves CSS custom properties before drawing.
- Inline `getStyle().set(...)` for a look that a component theme variant or a
  reusable CSS class already provides.
- A repeated inline style across several components instead of one CSS class in
  the project theme.
- Claiming styling works from a green compile / `clean test` — neither renders
  anything.
- Editing generated frontend files — they are regenerated on every build.
