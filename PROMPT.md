# Task: Implement the Home Screen from token.json in Jetpack Compose

You are working in a fresh Jetpack Compose (Kotlin, Material 3) Android project.
The file `token.json` (in the project root, or find it with a search) is a
TeleportHQ Figma-plugin export of our Home Screen design. It is a JSON tree
that describes the design as text: nested nodes, layout info, styles, colors,
typography, spacing, text content, and image references.

Your goal: implement the home screen so it matches the design faithfully, but
built as clean, reusable, idiomatic Compose, not one giant composable
transcribed node by node.

## Step 1: Analyze before coding

1. Read `token.json` fully. Determine its structure (UIDL-style node tree vs.
   flat design tokens) and summarize it to yourself.
2. Extract the design system first:
   - Colors (including opacity, gradients)
   - Typography (font family, size, weight, line height, letter spacing)
   - Spacing/padding/margin values, corner radii, elevations/shadows, borders
   - Icon and image references
3. Identify repeated structures in the tree (cards, list rows, chips, section
   headers, buttons, tab bars). Every pattern that appears 2+ times, or is
   clearly a UI component, becomes ONE reusable composable with parameters.
4. Before writing code, produce a short plan: the theme values you extracted,
   the component list, and the screen layout hierarchy. Then implement.

## Step 2: Theme layer (ui/theme)

- Map extracted colors to `Color.kt` and a Material 3 `ColorScheme`
  (light, plus dark if the tokens support it; otherwise derive a sensible one).
- Map typography to `Type.kt` (`Typography` / custom text styles).
  If the design uses custom fonts, add font resources under `res/font`
  if the files are available; otherwise fall back to the closest system font
  and leave a `// TODO: add font` note.
- Add custom design tokens Material doesn't cover (spacing scale, radii,
  extra colors) via a `CompositionLocal` (e.g. `LocalSpacing`, `LocalAppColors`)
  exposed through the theme object. NO magic numbers in UI code: every dp, color,
  and text style comes from the theme.

## Step 3: Reusable components (ui/components)

- One file per component, each with:
  - Stateless design: state hoisted, data passed via parameters, events via lambdas
  - A `modifier: Modifier = Modifier` parameter as the first optional parameter
  - Sensible defaults taken from the design
  - At least one `@Preview` (light, and dark where applicable)
- Prefer composition and slots (`content: @Composable () -> Unit`) over
  boolean-flag explosions.
- Use Material 3 components where they match the design (Button, Card, Chip,
  TopAppBar, NavigationBar, etc.) and customize them via theme, rather than
  rebuilding them from scratch.

## Step 4: Use native Android/Compose behavior. Do not fake it.

Wherever the design implies a native behavior, use the real thing:
- Vertical scrolling content: `LazyColumn` (never `Column` + `verticalScroll`
  for lists), with `key` and `contentType` set.
- Horizontal carousels/rails: `LazyRow` with `contentPadding` and
  `Arrangement.spacedBy`, plus `rememberSnapFlingBehavior` where snapping fits.
- Paging/swiping content (banners, onboarding-style cards): `HorizontalPager`
  with `PagerState` and page indicators; use `VerticalPager` where vertical
  paging is implied.
- If the design shows long/infinite lists, wire the structure for
  Paging 3 (`Pager`, `PagingSource`, `collectAsLazyPagingItems`) with fake
  in-memory data, so it is ready for a real backend.
- Tabs: `TabRow`/`PrimaryTabRow` synced with a pager where appropriate.
- Navigation bar/rail: `NavigationBar` + Navigation Compose (`NavHost`), with
  the home screen as the start destination; other tabs can be placeholder screens.
- App bars: `Scaffold` + `TopAppBar` with scroll behavior
  (`enterAlwaysScrollBehavior` / `exitUntilCollapsed`) if the design
  suggests collapsing headers.
- Pull-to-refresh: `PullToRefreshBox` if the design has a feed.
- Edge-to-edge: `enableEdgeToEdge()`, proper window insets handling
  (`WindowInsets`, `Scaffold` padding, `imePadding` where relevant).
- Images: Coil (`AsyncImage`) for remote images, `painterResource` for local
  drawables; add placeholders/error states and correct `ContentScale`.
- Icons: use vector drawables or `material-icons-extended` matches; import
  design icons as vector resources when they are SVG/vector references.
- Accessibility: `contentDescription` on images/icons, `semantics` and merged
  descendants for cards, touch targets >= 48dp, `Role` on clickables.
- Responsiveness: use `fillMaxWidth`, weights and constraints instead of
  hardcoded widths from the design. Consider `WindowSizeClass` for
  tablet/large-screen adaptation.

## Step 5: Architecture (keep it simple but structured)

- Package layout:

ui/theme/ Color.kt, Type.kt, Spacing.kt, Theme.kt
ui/components/ reusable building blocks
ui/home/ HomeScreen.kt (stateless UI), HomeViewModel.kt,
HomeUiState.kt, section composables
data/ fake/sample repository + models
navigation/ NavGraph.kt, destinations

- `HomeScreen` takes `HomeUiState` + event lambdas; a thin stateful wrapper
  collects state from `HomeViewModel` via `collectAsStateWithLifecycle()`.
- UI state is an immutable data class / sealed interface (`@Immutable` or
  `@Stable` where useful). Content that appears in the design (titles, prices,
  image URLs, labels) comes from sample data models, NOT hardcoded inside
  composables; each design string lives in the fake data or `strings.xml`.
- Use `strings.xml` for all user-visible static text (localizable).

## Step 6: Fidelity rules

- Match the design's hierarchy, spacing, sizes, corner radii, colors, and text
  styles from the tokens as closely as possible, but express them as theme
  tokens and layout constraints, not absolute positioning.
- Ignore absolute x/y coordinates from the export. Infer proper
  Row/Column/Box/Arrangement/Alignment from the structure and relative positions.
- If a token is ambiguous or missing (e.g. no font file, unknown image asset),
  make a reasonable choice and list it in your final summary. Don't stop to ask.

## Step 7: Build and verify

1. Add all required dependencies to the version catalog / gradle files
   (Compose BOM, Material 3, Navigation Compose, Coil, Paging 3, lifecycle
   compose, etc.) using latest stable versions.
2. Make sure the project compiles (`./gradlew assembleDebug`). Fix all errors and
   warnings you introduced.
3. Ensure every component has a working `@Preview`, plus a full-screen preview
   of the HomeScreen.

## Deliverable

- The complete implemented home screen, wired as the app's start screen.
- A final summary containing: the design tokens you extracted, the component
  list with a one-line purpose each, which native behaviors were used where,
  and any assumptions or unresolved gaps (missing fonts/assets, ambiguous nodes).

Constraints: Kotlin only, Jetpack Compose only (no XML layouts), Material 3,
no hardcoded colors/dp/text styles outside the theme, no giant monolithic
composables (keep composables focused and ideally under ~80 lines).
