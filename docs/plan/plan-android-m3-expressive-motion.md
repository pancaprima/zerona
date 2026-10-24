# Zerona Android: Material 3 Expressive motion & feel

Status: IMPLEMENTATION AUTHORISED. User selected option 1 on 24 October 2026: full official Expressive plus scoped Android toolchain/CI migration. Composer implements; Asa reviews and executes verification. **M0 dependency/toolchain pilot implemented in source (24 Oct 2026); not yet compile/device-verified until parent CI at exact SHA.** Proceed to UI motion only after exact-SHA dependency CI passes. No merge/release/deploy authorised.

### M0 actual artifact gate

Asa fetched the real Google Maven `material3:1.5.0-beta02` POM and `material3-android:1.5.0-beta02` AAR on 24 October 2026. Composer independently re-fetched the same artifacts on 24 October 2026 into scratch cache (`/root/.hermes/profiles/girlfriend/cache/scratch/zerona-m3-metadata`).

Verified from `material3-android-1.5.0-beta02.aar` → `META-INF/com/android/build/gradle/aar-metadata.properties`:

- `minCompileSdk=37`
- `minAndroidGradlePluginVersion=9.1.0`

Verified from `material3-android-1.5.0-beta02.pom` / `material3-1.5.0-beta02.pom` (Google Maven):

- Compose runtime/foundation/ui **1.12.0**
- Kotlin stdlib **2.2.20**

### M0 pinned toolchain (source-implemented)

| Component | Pin | Evidence |
| --- | --- | --- |
| AGP | **9.1.1** | AAR requires ≥9.1.0; [AGP 9.1.1 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes) state support for API **37.0** (9.1.0 max was 36.1). |
| Gradle | **9.3.1** | [AGP compatibility table](https://developer.android.com/build/releases/about-agp): AGP 9.1 → Gradle ≥9.3.1. |
| JDK | **17** | AGP 9.1 minimum JDK 17 (unchanged on CI). |
| compileSdk | **37** | Material3 AAR `minCompileSdk`; SDK package `platforms;android-37.0` in [repository2-3.xml](https://dl.google.com/android/repository/repository2-3.xml). |
| targetSdk / minSdk | **35 / 26** | Unchanged (runtime floor not raised). |
| Compose BOM | **2026.08.00** | BOM mapping: Compose **1.12.0** ([BOM mapping](https://developer.android.com/jetpack/compose/bom/bom-mapping)); matches Material3 POM Compose rev. |
| Material3 | **1.5.0-beta02** (explicit) | No published BOM yet maps Material3 1.5.x (BOMs through 2026.09.00 still pin material3 **1.4.0** per fetched POMs). |
| Kotlin (built-in) | **AGP 9 built-in** | [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin): no `org.jetbrains.kotlin.android`; UniFFI via `android.sourceSets` `kotlin.directories`. |
| Kotlin compiler (KGP) | **2.2.20** | AGP **9.1.1** POM runtime-depends **kotlin-gradle-plugin 2.2.10**; explicit root `buildscript` classpath per [AGP KGP override](https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin). Material3 POM **stdlib 2.2.20** does not bump KGP. |
| Compose compiler plugin | **2.2.20** | Same version as pinned KGP; `org.jetbrains.kotlin.plugin.compose` retained. |
| NDK (CI) | **27.2.12479018** | Pinned in `app/build.gradle.kts`; `android-app.yml` installs/sets `ANDROID_NDK_HOME` to this revision (not the runner default 27.3.x). |
| SDK tools (CI) | **Host `sdkmanager`** | `android-app.yml` resolves `/usr/local/lib/android/sdk`, exports absolute `SDKMANAGER` from `cmdline-tools`, and runs platform/NDK installs before Rust core. |

**Uncertainty / parent CI gates:** first green `:app:assembleDebug` + `testDebugUnitTest` on CI; possible `navigation-compose` / `activity-compose` interaction with Compose 1.12; UniFFI Kotlin on `kotlin.directories` under built-in Kotlin; no VPS Gradle run by Composer.

Decision resolved: user selected (1), latest official Expressive plus scoped Android toolchain/CI migration. Keep targetSdk/minSdk unchanged unless independently necessary. No silent downgrade to an older preview. Implement dependency/build compatibility in isolation, verify CI, then continue authorised motion milestones without another approval. Preserve Edge Dock/palette/native scroll and leave pending PR33 untouched. Any need for Rust/UniFFI/iOS changes remains an escalation.

Research date: 24 October 2026 UTC.
Repository: pancaprima/zerona. Source baseline: `2ad194e72e6b2bd07d445e15b74ac37ff49b93fa` (freshly fetched fork `origin/main`). Worktree: `/root/zeron-android-m3-expressive`; branch: `docs/android-m3-expressive`.

## 1. Recommendation in one sentence

Keep Zerona's accepted calm lilac/peach/mint composition and opaque Edge Dock, but give important actions a coherent Material expressive spring language: tactile controls, deliberate navigation, contextual composer states, and polished loading; keep long-form reading and streaming visually quiet.

Expressive is not synonymous with maximum bounce, saturated colours, oversized chat text, decorative spinning, or animating every update. The goal is clearer cause/effect and perceived responsiveness, not delayed access to content.

### Goals
- Establish one motion language shared by Compose shell and custom native controls.
- Add personality to press/release, session entry/back, Send, Queue/Steer, and Latest visibility.
- Strengthen action/shape/type hierarchy without replacing accepted screen composition.
- Preserve accessibility, reading position, messaging correctness and low-end-device responsiveness.

### Non-goals / preserved contracts
- No new search, microphone, attachment picker, FAB menu, navigation tabs, or unsupported actions.
- No backend, auth, FCM, runtime profile, Rust layout or UniFFI contract changes.
- No redesign of the accepted sessions hierarchy; retain grouped lilac surfaces, real project grouping, stable keyed lazy rows, honest status labels, account control, insets, and timestamps.
- Keep Edge Dock opaque, full-width and bottom-attached; retain symmetric field padding, one-line text/send alignment, multiline growth, queue/error states and IME/navigation backing. No floating glass/pill replacement.
- Preserve native transcript anchors, hit testing, copy/link/image actions, text metrics and selection. No spring scrolling or per-token message bounce.
- This research proposes replacing the earlier refresh's tween-only motion scope with a separate future milestone. It does not silently amend the accepted older design or claim approval to implement.

## 2. Official research and version gate

Sources below were read from Google's official pages. Current release/API pages take precedence over older launch blog snippets.

- [S1] Material motion physics overview: https://m3.material.io/styles/motion/overview/how-it-works
- [S2] Start building with M3 Expressive: https://m3.material.io/blog/building-with-m3-expressive
- [S3] Adding motion physics with Jetpack Compose: https://m3.material.io/blog/m3-expressive-motion-theming
- [S4] Compose Material3 release notes: https://developer.android.com/jetpack/androidx/releases/compose-material3
- [S5] MotionScheme API: https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme
- [S6] MaterialExpressiveTheme API: https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable
- [S7] Compose animation quick guide: https://developer.android.com/develop/ui/compose/animation/quick-guide
- [S8] MotionDurationScale API: https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale

### What to apply from Google

1. **Physics, not arbitrary duration presets.** Spatial springs handle position, rotation, size and corners; effects specs handle alpha/colour without overshoot. Use fast/default/slow roles for small controls, partial-screen surfaces and large transitions respectively. Springs should retarget naturally when interrupted. [S1, S3, S5]
2. **Use expressive hierarchy, not decoration everywhere.** Combine shape contrast, emphasized typography, tonal grouping and component flexibility; reserve the richest treatment for one or two important moments. [S2]
3. **Theme custom controls deliberately.** MaterialExpressiveTheme can provide the expressive scheme to Material components; custom Compose animations must consume theme specs, and Android View controls need a separate bridge. A theme switch alone does not animate the whole app. [S1, S3, S5, S6]
4. **Minimise unnecessary layout work.** Draw/layer animation is preferable for purely visual feedback; real size changes must correctly participate in layout. Do not use visual translation to hide geometry mismatches. Lazy-row entry effects must not replay every time rows re-enter composition. [S7]
5. **Respect system motion scaling.** Compose has MotionDurationScale; zero scale finishes motion at the next frame callback. Audit the actual Compose and native animator paths before adding any extra scaling. [S8]

### Dependency decision: do not copy 2025 alpha examples

As checked on 24 October 2026, the official release table lists Material3 **1.4.0 stable** and **1.5.0-beta02 beta**, with latest update 21 October 2026. The 1.4.0 release adopted MotionScheme for component motion, but its beta01 removed public experimental Expressive APIs; they continued on the 1.5 line. Therefore 'upgrade to 1.4' is not equivalent to getting the complete public Expressive feature set. [S4]

The latest MotionScheme API documentation is marked 1.5.0-beta02; this is the current reference, not evidence that no earlier preview ever contained motion APIs. Check exact candidate signatures/annotations when implementing. Avoid deprecated LocalMotionScheme examples: newer release notes direct consumers to MaterialTheme.motionScheme. [S4, S5]

Options:
1. **Stable-first:** compatible stable dependency upgrade plus custom restrained springs. Lower prerelease risk, but not the full official Expressive component surface; document that limitation.
2. **Official Expressive pilot (recommended):** isolated dependency-only candidate using Material3 1.5.0-beta02, then MaterialExpressiveTheme plus a small app-owned adapter. More faithful official components, but beta/toolchain migration risk. No automatic beta rollout to main.
3. **Hand-built expressive imitation on existing BOM:** smallest immediate dependency delta but more maintenance and behavioural inconsistency; do not present it as official M3 Expressive adoption.

Dependency gate must resolve the actual artifact graph and AAR metadata. Determine the compatible Compose BOM/runtime/foundation, Kotlin/compiler, AGP, compileSdk and navigation/activity requirements before selecting versions. The current compileSdk 35 may need to increase; do not invent a required SDK from an unrelated release-note subsection. Keep targetSdk/minSdk changes separate unless required and reviewed. Do not force a new Material3 version onto an incompatible older BOM.

## 3. Verified current implementation

Paths below are relative to the pinned baseline. Cursor read-only recon was spot-checked against the actual source; source evidence is not device evidence.

- `apps/android/app/build.gradle.kts:35-46,83-96`: compile/target SDK 35, minSdk 26, default arm64-v8a; Compose BOM 2024.12.01; Material3 has no explicit version; navigation-compose 2.8.5.
- `apps/android/build.gradle.kts:2-4`: AGP 8.7.3, Kotlin/Compose plugin 2.0.21.
- `ui/theme/Theme.kt:77-127`: existing shape/type tokens and light/dark MaterialTheme; no explicit expressive theme or motion scheme.
- `ui/ComposerBar.kt:78-107`: tween-backed animateContentSize around the composer stack, including inset modifiers. `:194-217` owns contextual Queue/Steer; `:246-299` preserves accepted field alignment/padding.
- `ui/SessionScreen.kt:173-207`: native AndroidView transcript, then Compose Latest visibility using fade/slide tween; `:243-261` loading and pagination hints.
- `ui/ZeronaApp.kt:32-46`: NavHost destinations with no app-authored transition specifications. This does NOT prove zero animation: navigation-library defaults may still run.
- `ui/SessionsScreen.kt:90,216-259,437-467`: safe drawing insets, account menu, accepted headline, row ripple/pressed fill already present. Add to these rather than replacing them.
- `transcript/RowView.kt:133-163`: ValueAnimator alpha reveal for fresh streaming content; not Compose.
- `transcript/ToolViews.kt:205-213,304-320`: tool chevron immediately changes rotation; shimmer uses an infinite ValueAnimator.
- `transcript/TranscriptListView.kt:299-316`: existing scroll-to-bottom implementation. Its correctness is outside the proposed motion rewrite.

The shortened `ui/` and `transcript/` paths above are under `apps/android/app/src/main/java/sh/zeron/zerona/`.

No explicit app-owned cross-stack reduced-motion policy was found in the surveyed Kotlin source. That is NOT proof framework animations ignore system settings: Compose and ValueAnimator already have platform behaviour. The gap is verified policy, lifecycle handling and testing, not necessarily a need for manual duration multiplication.

## 4. Proposed interaction catalogue

All motion below is a design proposal, not already implemented behaviour. Prefer official token specs over hand-entered stiffness/duration numbers. Tune visual amplitude on device, not by increasing every animation duration.

### P0: foundation and two hero interactions

**1. Central motion policy + expressive theme**
- Insertion: `ui/theme/Theme.kt`; proposed `ui/theme/ZeronaMotion.kt`; proposed native motion adapter only if required.
- One set of fast/default/slow spatial/effects roles; reduced-motion/static endpoints; clear Compose/native ownership.
- First verify framework scaling. Never scale twice. Do not persist a second independent global animation-speed setting in v1.

**2. Tactile session rows and icon buttons**
- Insertion: `ui/SessionsScreen.kt`, Send control in `ui/ComposerBar.kt`.
- Keep ripple and tonal pressed indication. Add tiny spring compression/release or corner response to eligible controls, with no layout movement of neighbouring rows and no shrinking of semantic touch targets.
- Starting prototype only: roughly 1–2% visual compression; prefer native Expressive button shape behaviour where available, and avoid stacking a second spring on an already animated component.
- Release/cancel must return to rest during fast tap, drag cancellation, navigation and disabled-state changes. No animation delay before click execution.

**3. Sessions → chat → back continuity (hero A)**
- Insertion: `ui/ZeronaApp.kt`, narrowly scoped screen wrappers.
- Explicit directional forward/back transitions with restrained spatial movement plus effects fade. Preserve chat state and list restoration. Loading shell can transition; transcript content must remain stable once visible.
- RTL-aware direction; interrupted back/forward transitions must settle cleanly. Predictive back is a compatibility investigation, not a promised feature of existing dependencies.
- Do not implement row-to-whole-transcript shared-element morph in v1: it adds lifecycle/AndroidView/anchor risk with little reading value.

**4. Send/delivery feedback (hero B)**
- Insertion: `ui/ComposerBar.kt`; presentation-only helpers in `ui/ComposerPresentation.kt`.
- Expressive press/release and effects transition between disabled/enabled/in-flight visuals. Animate only visual state supported by actual send state; no invented delivered checkmark or success before a real result.
- Keep immediate in-flight lockout, draft retention on failure, queue/steer semantics, readable disabled state and stable focus.
- No paper-plane fly-through across the transcript; no green success explosion.

### P1: contextual surfaces and hierarchy

**5. Queue/Steer selection and banner/queue appearance**
- Insertion: `ui/ComposerBar.kt`.
- Fast effects for selection colour, short contained spatial reveal for contextual row and banners. Preserve typing-only visibility and host capability guards.
- Avoid nesting multiple size springs on the same content. Separate real content-size change from IME/system-inset motion so the keyboard does not get a second delayed spring. Keep accepted text/send alignment.

**6. Latest appearance, not scroll physics**
- Insertion: `ui/SessionScreen.kt` only for control appearance.
- Replace ad hoc appearance tween with consistent effects + small spatial reveal. Keep an opaque readable surface and stable click target while visible.
- Do not change TranscriptAnchor, follow-tail thresholds, settle windows or scrollToBottom. Tapping Latest continues to use existing native behaviour; this plan does not fix or revalidate historical scroll bugs.

**7. Loading / empty / error / connected states**
- Insertion: `ui/SessionScreen.kt`, `ui/SignInScreen.kt`, `ui/LoadErrorScreen.kt`, current sessions status line.
- Evaluate official expressive LoadingIndicator for genuine initial waits if supported by the chosen version; small transitions between existing loading/content/error states.
- Reduced motion gets a static meaningful progress/status presentation. Unknown progress never becomes a fabricated percentage.
- Keep pagination hint unobtrusive; never blank already-visible messages while older content loads. No always-pulsing Live dot: connectivity is not execution activity. No invented retry button without an existing retry action.

**8. Shape, typography and surface hierarchy**
- Insertion: `ui/theme/Theme.kt`, `ui/theme/ZeronaColorTokens.kt`, existing screen-local tokens only where justified.
- Preserve lilac/peach/mint and accepted sessions group composition. Contrast a primary action's silhouette with quieter secondary controls instead of rounding every element identically.
- Use emphasized typography for already-important headings/actions; retain compact body text and native Geist metrics. Do not globally inflate transcript font size or animate font weight per streamed token.
- Maintain text contrast on actual light/dark fills and visible focus/pressed states. Wallpaper dynamic colour is optional future work, not a requirement for Expressive or a reason to discard Zerona's palette.
- Use static Material Symbols vectors for existing actions as a separate polish item after dependency stabilization; do not add the whole icon catalogue or claim static vectors have variable-font morph capability.

**9. Menus, org picker, image preview surfaces**
- Insertion: existing account DropdownMenu and OrgPickerDialog; native `NativeChrome.kt` / `ImageViewerDialog.kt` only in a scoped follow-up.
- Let updated Material components supply their own motion first; avoid double entrances. Match tonal surfaces, corners and scrim hierarchy without migrating all native dialogs to Compose.
- Preserve dismiss/back/focus, image gestures, secure auth and existing actions. Image shared-element transitions are deferred until a native bounds/lifecycle spike proves safe.

### P2: custom native parity, separately gated

**10. Tool disclosure and streaming lifecycle**
- Insertion: `transcript/ToolViews.kt`, `transcript/RowView.kt`; native policy adapter if needed.
- Animate chevron rotation using a fast spatial equivalent, while the existing core remains the authority on expanded content geometry. Smooth content-height morphing is NOT part of this first pass.
- Keep streaming to its existing subtle alpha treatment, with no replay on every rebind and correct recycled-view cleanup. Respect disabled motion and visibility/lifecycle; stop shimmer when detached/not meaningful.
- Compose theme specs do not automatically affect ValueAnimator. Either use platform springs with verified equivalent parameters or a documented native effects adapter; do not pass Compose-only animation specs to Views.

**11. Optional haptics, not baseline dependency**
- Small platform-standard feedback only for deliberate existing long-press/action interactions if a later prototype benefits. Respect device/user settings and avoid duplicate OS feedback.
- No vibration per token, tool completion, ordinary row tap, or queue refresh. Separate approval/testing if introduced.

## 5. Implementation sequencing and insertion points

1. **M0: baseline capture + dependency spike.** Record exact latest source/APK SHA, light/dark native screen recordings, existing behaviour and target dependency graph. Create dependency-only pilot; compile/test on CI; inspect merged-manifest/inset behaviour. Stop on incompatible toolchain requirements and review concrete scope, rather than broadly upgrading everything.
2. **M1: theme + policy + tactile controls.** Land official theme/token adapter and accessibility/lifecycle tests; row and Send tactile feedback. No native transcript edits.
3. **M2: navigation + composer + Latest.** Establish explicit forward/back transitions; contextual controls and Send state; isolate IME motion. Device-test these before widening scope.
4. **M3: loading + visual hierarchy + existing overlays.** Refine current surfaces and emphasized type; preserve accepted layout. Do not add list stagger by default: it can replay on refresh and reduces usability in dense sessions lists.
5. **M4: optional native controls.** Chevron/shimmer/reveal policy only after source ownership/lifecycle review, with separate anchor/hit-test regression and device gates. Any shared core geometry/API change is an escalation requiring a revised plan, not an implementation detail.
6. **M5: acceptance and handoff.** Compare baseline vs candidate on the same device and viewport; attach exact-SHA APK and short recordings. No merge/release until separately authorised.

Each milestone should be a reviewable narrow patch. Recheck open PRs and current fork main before implementation, especially the open FCM/self-host branch that touches app files. This research baseline does not include every pending branch.

## 6. Verification and acceptance

### Existing cheap checks
- `GRADLE_USER_HOME=/root/.gradle python3 scripts/android/test-design-helpers.py --cache-dir <existing-scratch-cache>`
- `bash apps/android/scripts/selfcheck-native-transcript.sh` (static inventory only, not runtime UI validation).
- Register new pure Kotlin policy tests in both source/class lists of the helper runner if applicable.

### CI, not heavy VPS builds
- Existing `.github/workflows/android-app.yml` assembles the APK with generated core artifacts. `:app:assembleDebug` depends on `testDebugUnitTest` in `app/build.gradle.kts`.
- M0 pre-assemble gates in that workflow (not claimed until green at exact SHA): install `platforms;android-37.0` and verify API 37 via `source.properties` + `android.jar`; `buildEnvironment` shows `kotlin-gradle-plugin:2.2.20`; `dependencyInsight` on `material3-android`, `runtime-android`, `ui-android`, `foundation-android`, and `kotlin-stdlib` with version markers; HTTP fetch of `material3-android` AAR metadata (`minCompileSdk`, `minAndroidGradlePluginVersion`); then `:app:assembleDebug` (which runs unit tests).
- Existing command: `gradle -p apps/android :app:testDebugUnitTest -PzeronCoreDir=<actual-artifact-path>`; assembly equivalent uses `:app:assembleDebug`.
- Dependency pilot must produce actual resolved versions, compile/AAR metadata results and exact-SHA APK evidence. Do not claim compatibility from a plan or API reference alone.
- Compose interaction/animation instrumentation coverage is not currently established; add scoped test infrastructure in the implementation milestone instead of pretending JVM helper tests execute Compose motion.

### Device matrix
- Primary Galaxy A07 / lower-end physical device; secondary Pixel 3 when available; newer Android for back/edge-to-edge compatibility if available. Never claim an unavailable device was tested.
- Light/dark; font scale normal and 200%; portrait/landscape; gesture and three-button navigation where available.
- Animator scale 0, normal and slowed mode; toggling while app is alive. All actions work without waiting for animation and settle to a correct final state.
- Rapid tap/cancel, back during entry, quick chat switching, restored session list, app background/foreground during streaming/loading.
- Empty/typed/multiline draft; IME opening/closing, pasted long draft, queue/steer visibility, sending lockout, failure/draft retention, failed queue actions, demo-disabled composer.
- Long chat, read far above tail while streaming, Latest appearance/tap, opening a session at tail, loading older history, copy/links/tool expand/image preview.
- TalkBack semantics, readable state labels, focus restoration and minimum 48dp target for standalone tappable actions. Compact visual chips must retain adequate interaction bounds without overlapping neighbours.

### Performance and visible success criteria
- Collect comparable baseline/candidate frame timing traces or Android frame statistics on the same device/build mode. Report actual frame-time/jank deltas; no guaranteed 60fps claims before measurement. Screen recordings alone do not prove frame timing.
- No repeated layout/re-measure of whole transcript for a button press. No per-token spring or looping decorative animation while idle.
- Foreground reading position, hit testing, text wrapping and queue/send correctness remain unchanged. No new blank transcript or IME backing bleed.
- Springs retarget on interruption without snap-to-start, and effects remain within valid alpha/colour behaviour.
- Device-observed checks are separate from source-implemented, compiled, and helper-tested checkboxes.

## 7. Risks, rollback and safe stopping points

- **Beta/toolchain risk:** isolate dependency pilot; stop before UI migration if artifact metadata or source compatibility fails. Stable-first is a documented fallback, not silent partial Expressive delivery.
- **Excess motion/density drift:** retain baseline recordings and accepted layouts; remove optional effects rather than changing the whole palette or composer.
- **IME double motion:** preserve opaque inset owner and test keyboard lifecycles before widening contextual size animations.
- **Native/core geometry mismatch:** do not animate bounds owned by core; defer height morphs and shared elements. Escalate any required Rust/UniFFI/iOS change.
- **Reduced-motion duplication:** rely on framework scaling where already effective; test custom adapters and static endpoints rather than multiplying duration globally.
- **Rollback:** revert the relevant milestone commit(s); dependency pilot is independently revertible. No persisted data/schema changes are proposed, so existing chats/config must not migrate.

Safe stops: after research (current state), after isolated dependency CI, after shell-only device validation, and before any native transcript extension. No deploy, merge, release, or unrelated screen redesign is implied.

## 8. Decisions and status

Recommended default direction: **Calm Expressive**, official Expressive beta evaluated on an isolated pilot, two hero interactions (session navigation and Send), quiet transcript. These are proposals for review, not recorded user approvals.

Alternative intensity: stronger expressive shapes/motion can be prototyped on controls only if requested; avoid treating 'maximise' as permission to abandon previous accepted calm design.

Completed for this deliverable:
- [x] Official Google design, motion, API and release research.
- [x] Read-only recon pinned to fresh fork main, major claims spot-checked.
- [x] Prioritised interaction catalogue, ownership boundaries, dependency options, test gates and rollback documented.
- [x] Plan file created locally.
- [x] M0 toolchain/dependency pilot (Gradle scripts + CI evidence step); **compiled/device-observed: pending parent CI**.
- [ ] Native motion prototype / implementation (M1+).
- [ ] Dependency resolution/build/tests for a candidate (**parent CI**).
- [ ] Physical-device accessibility/performance/feel acceptance.
- [ ] Commit, PR, merge or release.
