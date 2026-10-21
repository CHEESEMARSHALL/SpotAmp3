# SpotAmp Android identity and versioning decision

**Status:** Decision required before changing the installed application identity  
**Scope:** Proposal only; no package, signing, or version values are changed here.

## Current repository values

- Android namespace: `com.example`
- Production application ID: `com.aistudio.plexmusicplayer.vctplx`
- QA application ID: production ID plus `.qa`
- Benchmark application ID: production ID plus `.benchmark`
- Version: `1.0` (`versionCode` 1)
- Release signing is enabled only when explicit release signing credentials are supplied; see [BUILD_SIGNING.md](BUILD_SIGNING.md).

The application ID is the device and store identity. Renaming it is not an in-place update: Android will treat the renamed build as a different app, so users may need to install it separately and existing private app data will not automatically follow. Changing the namespace alone is a code/package migration and does not rename the installed app.

## Recommendation

Keep the production application ID and `versionCode` unchanged during Wave 1. First establish who owns the existing ID and whether any distributed build is installed by testers or users. Continue using the `.qa` and `.benchmark` suffixes for isolated validation variants.

If a new production ID is approved, treat it as a deliberate new-app migration: select the final reverse-domain ID, confirm store/account ownership, define user-facing migration and data-export expectations, provision the production signing key, and set a monotonically increasing version code for future releases of that new ID. Do not assume credentials or cached library state can be transferred automatically.

## Decisions needed before migration

1. Is `com.aistudio.plexmusicplayer.vctplx` already distributed or associated with a store listing?
2. Who controls the release signing identity and store account for the current ID?
3. Should SpotAmp preserve the existing install/update path, or launch as a separate app under a new ID?
4. If a new ID is chosen, what final reverse-domain ID and release version policy should be used?

No signing secrets, store credentials, or user data should be added to this proposal.
