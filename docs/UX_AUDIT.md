# SpotAmp UX Audit — Phase 1 (Audit Only)

**Date:** 2026-09-07  
**Revision audited:** `d6d84cb`  
**Scope:** Current checkout, source inspection, live QA-device interaction, screenshots, and available QA artifacts.  
**Status:** AUDIT COMPLETE — NO PRODUCT UX CHANGES APPLIED.

## 1. Executive summary

SpotAmp has a credible music-player structure: persistent mini-player support, clear primary destinations (Home, Library, Search, Downloads, Settings), dedicated artist/album detail routes, queue editing, a full Now Playing surface, background media-service support, and deliberate offline/download concepts. The existing Now Playing/Queue screenshot is visually legible, has strong title hierarchy, and makes the current queue item easy to identify.

The largest UX risks are state communication and information density rather than missing functionality. Pairing/connection, offline-only search, sync diagnostics, download state, and playback loading are present in the code but are not yet proven to be understandable to a first-time user. Several important controls expose generic semantics (notably Repeat), while the full-player play/pause control is implemented as a clickable `Box`, making its accessibility role/state less explicit than the mini-player control. Queue reordering is long-press/drag based without an obvious visible affordance. Settings also mixes end-user preferences with diagnostics and backend terminology, which risks making a normal listening product feel like an administration console.

This remains an evidence-limited audit, not a claim that these behaviors are broken. A Samsung Android device was available for a short connected-state pass. Fresh-install pairing, TalkBack, font scaling, network failure recovery, and full physical touch-ergonomics coverage remain open validation items.

## 2. UX scorecard

Scores are diagnostic and based on source/evidence review only where marked `*`.

| Surface | Score | Rationale |
|---|---:|---|
| First launch | N/A | No live fresh-install run available. Pairing implementation exists. |
| Navigation | 7* | Small, predictable top-level model; manual stack resets on bottom navigation. |
| Home | 7* | Live screen clearly prioritizes a “Made for you” play action and recent plays. |
| Library | 7* | Live device showed distinct categories and 43,266 tracks without blocking. |
| Search | 7* | Clear field, clear action, grouped Artists/Albums/Tracks, offline toggle; result reshuffle/loading needs live review. |
| Artist | 6* | Dedicated detail route and play/radio actions; hierarchy not device-validated. |
| Album | 7* | Dedicated detail route with play, shuffle, download, playlist actions. |
| Mini player | 7* | Persistent, identifiable, explicit play/pause semantics; tap-target overlap unverified. |
| Now Playing | 7* | Strong primary player structure and queue continuation; accessibility/state detail needs work. |
| Queue | 6* | Current item and clear action are visible; reorder discoverability is weak. |
| Downloads | 7* | Live empty state explains what will appear; active/failed states unverified. |
| Offline | 5* | Offline-only search and settings indicators exist; end-to-end offline confidence unverified. |
| Pairing | 6* | QR pairing exists; first-use copy/recovery not live-tested. |
| Feedback | 6* | Loading and error state branches exist; immediate acknowledgement not measured. |
| Errors | 5* | Error text is surfaced in several paths; actionability/recovery needs device testing. |
| Accessibility | 5* | Many labels exist, but state descriptions/roles and scaling are not demonstrated. |
| Visual consistency | 7* | Repeated cards/rows/theme patterns; some competing visual systems in settings/player. |
| Touch ergonomics | N/A | Requires real phone. |
| Perceived performance | 7* | Connected screens rendered responsively after navigation; no precise latency instrumentation. |
| Overall polish | 6* | Solid feature foundation, with state clarity and density preventing a release-ready confidence score. |

## 3. Top 10 findings

### UX-001 — Full-player primary control has incomplete accessibility semantics

- **Severity:** HIGH
- **Category:** Accessibility, Playback, Feedback
- **Screen / workflow:** Now Playing → play/pause
- **Observation:** The full-player play/pause action is a clickable `Box` with a visual icon/test tag, while the mini-player uses an `IconButton` plus explicit button semantics. The full-player control therefore needs explicit role, label, and state description verification.
- **Evidence:** `NowPlayingScreenLayout.kt` implements the main control with `.clickable { playbackManager.togglePlayPause() }`; the mini-player explicitly sets `Role.Button` and `contentDescription` in `BottomMiniPlayer.kt`.
- **User impact:** TalkBack users may receive weaker focus/role/state information; all users may have less confidence during loading or paused states.
- **Recommended direction:** Add explicit button semantics and a state description such as “Playing”, “Paused”, or “Loading”; verify with TalkBack and Compose semantics tests.
- **Confidence:** High
- **Implementation scope:** Small
- **User value / effort / regression risk:** HIGH / SMALL / LOW

### UX-002 — Repeat state is not self-describing

- **Severity:** HIGH
- **Category:** Playback, Accessibility, Discoverability
- **Screen / workflow:** Now Playing → repeat
- **Observation:** The repeat icon content description is simply “Repeat”; the icon and tint distinguish off/on and Repeat One visually, but Repeat All versus Repeat One and the current state are not exposed in the label.
- **Evidence:** `NowPlayingScreenLayout.kt` uses `contentDescription = "Repeat"` and switches the icon for `REPEAT_MODE_ONE`; no state description is visible in the inspected implementation.
- **User impact:** Users can activate a mode without knowing which mode is active, especially with color/vision limitations or screen readers.
- **Recommended direction:** Label the action with the next/current state and expose state description: “Repeat off”, “Repeat all”, “Repeat one”. Do not rely on color alone.
- **Confidence:** High
- **Implementation scope:** Small
- **User value / effort / regression risk:** HIGH / SMALL / LOW

### UX-003 — Queue reordering is discoverable only through a hidden gesture

- **Severity:** HIGH
- **Category:** Queue, Touch Interaction, Discoverability, Accessibility
- **Screen / workflow:** Queue → reorder
- **Observation:** Queue rows support `detectDragGesturesAfterLongPress`, but the visible row presents numbering, artwork, metadata, and overflow without a drag handle or explanatory affordance.
- **Evidence:** `NowPlayingScreenLayout.kt` attaches long-press drag handling directly to the row; no visible reorder handle or instruction was found in the inspected queue row.
- **User impact:** Users may not discover reordering, and screen-reader/keyboard users may have no equivalent action.
- **Recommended direction:** Add a clear drag handle and an accessible move-before/move-after action or menu alternative; validate shuffled-queue explanation separately.
- **Confidence:** High
- **Implementation scope:** Medium
- **User value / effort / regression risk:** HIGH / MEDIUM / MEDIUM

### UX-004 — Settings exposes too much diagnostic/backend language in the primary experience

- **Severity:** HIGH
- **Category:** Cognitive Load, Settings, Pairing, Trust
- **Screen / workflow:** Settings → connection/readiness/sync
- **Observation:** Settings includes “Device Readiness”, “Sync Diagnostics”, backend names, page counters, signatures, current prefixes, and a detailed event log alongside normal configuration.
- **Evidence:** `SettingsScreen.kt` renders readiness and sync-diagnostic cards, including “Current Page”, “Current Prefix”, “Last Signature”, and “Detailed Log”.
- **User impact:** Normal users may interpret routine music setup as technical administration and may not know which status matters for listening.
- **Recommended direction:** Put a concise user-facing connection/download/playback status first; progressively disclose diagnostics behind “Advanced diagnostics” or a developer/support section.
- **Confidence:** High
- **Implementation scope:** Medium
- **User value / effort / regression risk:** HIGH / MEDIUM / MEDIUM

### UX-005 — Offline mode is represented as an unlabeled switch in Search

- **Severity:** MEDIUM
- **Category:** Offline, Search, Accessibility, Discoverability
- **Screen / workflow:** Search → offline-only toggle
- **Observation:** The header shows a cloud-download icon and a scaled switch, but the visible copy does not clearly state “Search downloaded music only” or what changes when enabled.
- **Evidence:** `SearchScreen.kt` renders the icon and `Switch` without an adjacent text label; the behavior filters tracks to completed downloads.
- **User impact:** Users may miss the feature or misunderstand why results disappear while offline.
- **Recommended direction:** Add a visible label and concise supporting text/state description; make the interaction accessible without relying on the icon.
- **Confidence:** High
- **Implementation scope:** Small
- **User value / effort / regression risk:** HIGH / SMALL / LOW

### UX-006 — Search result groups deliberately truncate to three items

- **Severity:** MEDIUM
- **Category:** Search, Information Architecture
- **Screen / workflow:** Search → results
- **Observation:** Artists, albums, and tracks are grouped, but the main screen renders `take(3)` for each group and delegates to a separate results route.
- **Evidence:** `SearchScreen.kt` uses `artistsMatches.take(3)` and `albumsMatches.take(3)`; the same pattern should be verified for tracks and “see all” affordances.
- **User impact:** Users may not know whether a desired result is absent or merely outside the preview, especially on broad queries.
- **Recommended direction:** Make “See all” count/affordance prominent and preserve query/category context when returning.
- **Confidence:** Medium
- **Implementation scope:** Small
- **User value / effort / regression risk:** MEDIUM / SMALL / LOW

### UX-007 — Search loading/error/no-result states are visually similar in importance

- **Severity:** MEDIUM
- **Category:** Loading, Error Handling, Feedback
- **Screen / workflow:** Search → typing/results
- **Observation:** Loading is a bare spinner, errors are centered text, and no-results is centered text. The implementation does not show an explicit retry or recovery action in the inspected branch.
- **Evidence:** `SearchScreen.kt` branches to `CircularProgressIndicator`, `searchError` text, or `No matches found` text.
- **User impact:** Users may not know whether to wait, retry, change the query, or clear it; query changes may feel like visual reshuffling.
- **Recommended direction:** Use labeled, actionable states: “Searching…”, “Try again”, and “No matches—clear or change your search”. Preserve prior results during refresh where appropriate.
- **Confidence:** Medium
- **Implementation scope:** Small
- **User value / effort / regression risk:** HIGH / SMALL / LOW

### UX-008 — Mini-player expansion affordance is implicit

- **Severity:** MEDIUM
- **Category:** Discoverability, Playback, Touch Interaction
- **Screen / workflow:** Mini player → Now Playing
- **Observation:** The entire mini-player row is clickable, but there is no explicit expand/chevron affordance or visible “Now Playing” cue.
- **Evidence:** `BottomMiniPlayer.kt` applies `.clickable { onExpand() }` to the parent column and renders artwork, metadata, and play/pause only.
- **User impact:** New users may treat it as a passive status row and miss the full player; broad click target can also create ambiguity with the bottom navigation boundary.
- **Recommended direction:** Add an explicit accessible label (“Open Now Playing”), subtle chevron/handle, and verify bottom-bar separation on a phone.
- **Confidence:** High
- **Implementation scope:** Small
- **User value / effort / regression risk:** MEDIUM / SMALL / LOW

### UX-009 — Existing Now Playing screenshot shows strong readability but unused vertical space

- **Severity:** POLISH
- **Category:** Visual Hierarchy, Spacing, Perceived Performance
- **Screen / workflow:** Now Playing → queue
- **Observation:** The supplied screenshot has a readable dark theme, strong current-track highlight, and clear queue rows, but the lower half of the viewport is largely empty after the queue content.
- **Evidence:** `app/src/test/screenshots/now-playing-queue.png` (1078×2399 source image, displayed resized by the app).
- **User impact:** The player can feel less content-dense and less intentionally composed on tall phones; queue continuation may feel disconnected from the controls.
- **Recommended direction:** Validate on actual target aspect ratios before changing layout; consider adaptive spacing or a more purposeful queue affordance only if the gap persists.
- **Confidence:** Medium
- **Implementation scope:** Medium
- **User value / effort / regression risk:** LOW / MEDIUM / MEDIUM

### UX-010 — Connection identity is not yet demonstrated as a persistent, user-facing state

- **Severity:** MEDIUM
- **Category:** Pairing, Offline, Trust, Error Handling
- **Screen / workflow:** Startup, Home, Settings, playback recovery
- **Observation:** Settings can display “SpotCore connected” when companion configuration is present, but source review does not establish a consistent, compact connection state across Home/player/offline recovery or distinguish paired from reachable/streaming.
- **Evidence:** `SettingsScreen.kt` renders `SpotCore connected`; `MainAppScreen.kt` routes configured state but does not by itself establish a persistent user-facing connection indicator.
- **User impact:** Users may not know whether a successful pairing is currently reachable, whether cached music remains usable, or what action recovers a lost connection.
- **Recommended direction:** Define a small state model and use plain-language status/recovery affordances only where actionable: paired, connected, reconnecting, offline.
- **Confidence:** Medium
- **Implementation scope:** Medium
- **User value / effort / regression risk:** HIGH / MEDIUM / MEDIUM

## 4. Must-fix before release

- UX-001: explicit semantics/state for the full-player play/pause control.
- UX-002: explicit Repeat mode/state communication, including TalkBack labels.
- UX-003: accessible and visible queue reordering affordance.
- UX-004: separate normal-user connection/status from advanced diagnostics.

## 5. Quick wins

- Label the Search offline-only switch and expose its state.
- Change repeat descriptions to stateful labels.
- Add “Open Now Playing” semantics and a subtle expansion affordance to the mini-player.
- Add explicit retry/clear/change-query actions to Search error and empty states.
- Add a visible queue drag handle plus accessible move actions.

## 6. Workflow measurements

| Journey | Taps | Waits | Result |
|---|---:|---|---|
| Launch → known track → play | Not completed | ~5 s to usable paired Home; track already active | Returning paired state only. |
| Launch → artist → album → specific track | Not measured | Not measured | No device/emulator available. |
| Now Playing → queue → move → return | Not completed | Not measured | Now Playing observed; move not executed. |
| Album → download → verify offline | Not completed | Not measured | Downloads empty state observed; no download started. |
| Disconnected → understand → reconnect → resume | Not completed | Not measured | No outage induced. |

### Live device interaction counts

On the paired QA device (1080×2340 px, 450 dpi), Home → Library, Library → Search, and Search → Settings each took one tap. Returning from Now Playing used the visible down-chevron, then one tap to select a destination. These are navigation measurements only.

Do not use these missing measurements as evidence of failure. They are audit gaps.

## 7. Accessibility findings

The source contains many useful content descriptions for navigation, artwork, track actions, and mini-player play/pause. The main gaps are stateful semantics and alternate interaction paths: full-player play/pause is a generic clickable surface, Repeat is generic, and queue reordering is gesture-only in the inspected implementation. Touch-target sizing, contrast under large text, focus order, TalkBack output, and switch labeling require device/scanner verification.

## 8. Screenshot evidence

- [Now Playing / Queue screenshot](../app/src/test/screenshots/now-playing-queue.png): current track is strongly highlighted, title/artist hierarchy is clear, queue rows are legible, and the layout leaves substantial unused space below the queue.
- [Live connected Home](../ux-results/latest/screenshots/home-connected.png): backend-connected status, recommendation hero, recent plays, mini-player, and five-item navigation.
- [Live Library](../ux-results/latest/screenshots/library.png): categorized library with a 43,266-track fixture.
- [Live Search empty state](../ux-results/latest/screenshots/search-empty.png): search field and unlabeled offline switch.
- [Live Settings](../ux-results/latest/screenshots/settings.png): readiness and sync diagnostics visible together.
- [Live Now Playing](../ux-results/latest/screenshots/now-playing.png): artwork, waveform, progress, and playback controls.

Screenshots were captured from the QA build on the connected device and stored under `ux-results/latest/screenshots/`.

## 9. Findings by screen

### Home

The screen is a first-class feed surface with recommendation/recent-play models and navigation callbacks to artists, albums, and playlists. Live section ordering, content usefulness, loading stability, and connected/disconnected states were not observable.

### Library

The navigation model gives Library a stable top-level destination and treats artist/album/playlist details as part of its selected state. Large-library density, sorting/filter discoverability, fast scroll, and metadata truncation remain untested.

### Search

Search has a clear field, clear button, live query effect, grouped result types, and an offline-only filter. Primary findings are the unlabeled offline switch, preview truncation discoverability, and bare loading/error/empty states.

### Artist

Dedicated artist navigation and play/radio/context actions are present. Live hierarchy, long-list behavior, and return-context preservation were not observed.

### Album

Album details expose artwork, play/shuffle, radio, download, playlist, and track actions. The source supports a rich album surface; multi-disc, compilation, same-title, explicit, and missing-artwork behavior requires fixture/device review.

### Player

Mini-player persistence is a strength. The full player has strong primary controls and an “Up Next” continuation, supported by the screenshot evidence. The full-player semantics, repeat state communication, loading acknowledgement, seek ergonomics, and motion require follow-up.

### Queue

The current row is highlighted and the queue exposes a clear action. Reordering is implemented through long-press drag without a visible handle; duplicate/shuffle semantics and accessible alternatives need review.

### Downloads

Downloads have a dedicated route and contextual actions, with completed-download filtering used elsewhere. The visual distinction among queued, active, failed, retrying, and offline-ready states was not live-tested.

### Offline

Offline-only Search and cached/download counts are represented. There is not enough evidence to judge whether the user can confidently predict what remains playable while SpotCore is unavailable.

### Pairing

QR pairing and companion configuration are implemented. First-launch explanation, failed-scan recovery, successful transition, and paired/reachable distinctions were not device-tested.

### Settings

Settings is comprehensive but mixes backend configuration, diagnostics, metadata review, collection suggestions, and ordinary preferences. Progressive disclosure is the clearest future direction.

## 10. Recommended implementation order (future work; not applied)

1. **Wave 1:** Accessibility/state semantics for player controls, Repeat, queue reorder, and offline switch.
2. **Wave 2:** Actionable Search loading/error/empty states and mini-player expansion discoverability.
3. **Wave 3:** Connection-state model and first-launch/reconnect/offline confidence flows.
4. **Wave 4:** Settings information architecture and Now Playing spacing/density refinement.
5. **Wave 5:** Device-specific motion, typography, touch, notification, and visual polish validation.

## 11. Things that should not be changed

- Keep the persistent mini-player concept; it supports continuous listening and quick pause/play.
- Keep the dedicated Search field with clear action and grouped result types.
- Keep the current-track highlight and “Up Next” continuation pattern.
- Keep album/artist navigation from track context; it matches music-app expectations.
- Keep explicit download/offline concepts rather than hiding them behind automatic behavior.
- Keep backend tokens out of diagnostics; the Settings copy explicitly states that secrets are not displayed.
- Do not flatten the product into a Spotify clone; recommendations should preserve SpotAmp’s companion/offline identity.

## 12. Device-validation gate before Waves 3–5

This gate was run against the connected Samsung QA device on 2026-09-07. Waves 1 and 2 are not implemented in this checkout; therefore these are validation attempts against the current build, not post-wave PASS claims.

| Requested validation | Result | Evidence / exact reason |
|---|---|---|
| TalkBack: full-player Play/Pause | BLOCKED | TalkBack package exists, but enabling it through `settings put secure enabled_accessibility_services ...` left the secure setting `null`; no TalkBack service became active. |
| TalkBack: Repeat/Shuffle | BLOCKED | Same device/service gate; no TalkBack output can be established. |
| TalkBack: queue reordering | BLOCKED | Same device/service gate; current source still uses long-press drag and no accessible move action was established. |
| TalkBack: Search downloaded-only switch | BLOCKED | Same device/service gate. Live screenshot confirms the switch is visually unlabeled. |
| TalkBack: mini-player expansion | BLOCKED | Same device/service gate. |
| Increased font scale: Home | PASS WITH FINDING | At `font_scale=1.3`, Home remained usable and did not clip primary content, but the backend status wrapped to two lines and the Downloads bottom-nav label wrapped awkwardly. |
| Increased font scale: Search | PASS WITH FINDING | Search remained usable; the field and empty state stayed visible, but bottom-nav labels became oversized/wrapped. |
| Increased font scale: Album | BLOCKED | No album-detail workflow was completed because the live search request failed under the QA network policy. |
| Increased font scale: Now Playing | BLOCKED | Full font-scale capture was not completed before restoring system scale. |
| Increased font scale: Queue | BLOCKED | Queue reorder/full queue capture was not completed at increased scale. |
| Increased font scale: Settings | PASS WITH FINDING | Settings remained scrollable, but dense diagnostic copy and long values need review at large text. |
| Physical primary Play/Pause | PASS | Visible primary control was tappable; live state changed between play and pause in the captured Now Playing pass. |
| Physical seek bar | BLOCKED | No reliable seek gesture result was established in this run. |
| Physical Repeat/Shuffle | PASS WITH FINDING | Controls were reachable and visibly changed tint/icon state; state meaning remains under-labeled. |
| Physical queue reorder handle | BLOCKED | No visible handle exists in the current UI and no reorder gesture was completed. |
| Physical mini-player | PASS | Mini-player was visible and opened Now Playing via one tap. |
| Physical bottom navigation | PASS | Home → Library → Search → Settings each completed with one tap. |
| Launch → Search → known track → play | BLOCKED | Search produced `Search failed. Try again or search your cached library offline.` for `Ill-Lit Ships`; the UI dump identifies the QA policy rejection of the configured non-local SpotCore origin. |
| Search → artist → album → specific track | BLOCKED | Same search/network-policy failure prevented reaching artist and album routes. |
| Now Playing → Queue → reorder → return | BLOCKED | Now Playing was reached, but queue reorder was not completed and no visible reorder handle is present. |
| Real SpotCore playback → Next → UI transition | PASS WITH FINDING | Real track was visible and Next was tapped; the player remained coherent, but the transition was not instrumented frame-by-frame and no exact latency claim is made. |
| Mini-player → Now Playing → back/down → another destination | PASS | Mini-player opened Now Playing; down-chevron returned; another destination was selected. |
| Notification/lock-screen media | BLOCKED | Environment did not expose a reliable lock-screen/notification inspection path in this run; no PASS is claimed. |

### Gate decision

**Waves 3–5 remain gated.** TalkBack activation succeeded on retry, but requested validation is only partial: Play is exposed as “Play” but not itself clickable in the accessibility tree, Repeat is generic, the Search switch is unlabeled/`NAF=true`, and queue reordering has no accessible alternative. Search-based artist/album workflows remain blocked by the exact QA network-policy rejection. The increased-font screenshots are retained under `ux-results/latest/screenshots/` as evidence. The most concrete new device finding is **UX-011**: at 130% font scale, Home status text and the Downloads bottom-navigation label wrap in ways that weaken scanability.

### Retry addendum — TalkBack evidence

TalkBack was enabled through Settings → Accessibility → TalkBack. The resulting UI dump confirmed: full-player Play is present with label “Play” but its labeled node is not clickable; Shuffle is labeled “Shuffle”; Repeat is labeled only “Repeat”; the Search downloaded-only switch is checkable but `NAF=true` and has no content description; mini-player exposes “Pause”/“Play”; and the Now Playing queue button exposes “View Queue”. Spoken audio output and gesture navigation were not independently verified, so these are semantics-tree observations rather than full TalkBack PASS results.

## 13. Final audit statement

**Observed:** source-level navigation, Compose semantics patterns, Search states, Settings status/diagnostic surfaces, player/queue controls, download/offline hooks, media-session presence, existing screenshot evidence, and live connected Home/Library/Search/Downloads/Settings/Now Playing screens.  
**Measured:** repository revision; device resolution/density; one-tap navigation transitions; approximately five seconds from explicit launch to usable paired Home after force-stop; font scale 1.3 behavior on Home/Search/Settings; QA search failure text and exact configured-origin rejection.  
**Device-tested:** QA build on a connected Samsung Android device; paired library browsing, navigation, mini-player expansion, Now Playing, Next, font scaling, and physical play/pause/control reachability.  
**Could not assess:** reliable TalkBack output because the service could not be activated (`enabled_accessibility_services` remained `null`), completed artist/album workflows because the QA network policy rejected the configured non-local origin, full seek verification, queue reorder completion, full large-text Now Playing/Queue/Album validation, and lock-screen/notification controls.

**AUDIT COMPLETE**  
**NO PRODUCT UX CHANGES APPLIED**

## 14. Updated post-Waves 1–2 and connected-device evidence

The historical gate above is retained as the original baseline. The current implementation and device evidence are authoritative in `docs/UX_AUDIT_POST_IMPLEMENTATION.md` and `docs/UX_DEVICE_VALIDATION.md`: Waves 1–2 have been implemented, TalkBack is out of scope per the product owner, and the connected Samsung rerun is sufficient to proceed with Waves 3–5 with scoped residual blockers.

Current evidence includes 130% captures for Home, Search, Album, Now Playing, Queue, and Settings; physical Play/Pause, seek, Repeat/Shuffle, mini-player, bottom-navigation, and button-based queue reorder; normal-scale Search → album → specific-track navigation; real SpotCore pairing/streaming/Next/background playback through the dedicated companion harness; and Media3/MediaStyle notification metadata. Remaining unclaimed behavior is limited to handle-specific long-press drag reorder, lock-screen transport controls in the Samsung charging/AOD environment, independent audible-output observation, and the generic UI-suite real-companion case when `companionBaseUrl` is omitted.
