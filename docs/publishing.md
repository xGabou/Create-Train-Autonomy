# Publishing to Modrinth and CurseForge

The project uses [ModPublisher 2.2.3](https://modpublisher.fdd-docs.com/), configured in `gradle/publishing.gradle`. It uploads the production jar from `build/libs`, runs `build` first, and labels uploads for Minecraft **1.21.1**, **NeoForge**, and **Java 21**. Create is a required dependency on both sites; CC:Tweaked, Create Additional Logistics, and Railways Additions are optional. The mod metadata requires Create **6.0.10**; the upload dependency relation identifies the Create project rather than pinning a platform file.

## Fill these in later

Create a project on each site first. ModPublisher uploads versions to existing projects; it does not create project pages.

| Location | Value to fill |
| --- | --- |
| `gradle.properties`: `modrinth_project_id` | Modrinth project ID or slug |
| `gradle.properties`: `curseforge_project_id` | Numeric CurseForge project ID, not its URL or slug |
| `gradle.properties`: `mod_version` | Version shared by the jar and mod metadata |
| `gradle.properties`: `publish_release_type` | `alpha`, `beta`, or `release`; currently `beta` |
| `CHANGELOG.md` | Release notes for the version being uploaded |
| Environment: `MODRINTH_TOKEN` | Modrinth token with permission to upload versions to the project |
| Environment: `CURSEFORGE_TOKEN` | CurseForge upload API token; `CURSE_TOKEN` also works |

Project IDs and release metadata may be committed. Keep tokens in your environment or CI secrets. The plugin does not read a `.env` file. Ordinary builds work with both project IDs blank and without tokens.

`publishMod` checks that both platforms are configured before either upload begins. The individual publishing tasks check only their own platform.

On the project pages, fill in the description, icon, categories, license, and source/issues links. `README.md` provides a starting description. The current mod license is **All Rights Reserved**; keep the site license consistent with `mod_license` or update both if you choose another license.

## Build and preview

Use Java 21. PowerShell commands from the repository root:

```powershell
.\gradlew.bat build

# Show the task graph without API calls, tokens, or filled project IDs.
.\gradlew.bat publishMod --dry-run --no-configuration-cache

# Once project IDs and tokens are configured, preview both upload payloads.
.\gradlew.bat publishMod -Ppublish_debug=true --no-configuration-cache
```

`publish_debug=true` is the default. ModPublisher's debug preview still contacts platform APIs to resolve metadata and dependencies, and requires project IDs and tokens. Gradle's `--dry-run` only previews task execution and works before anything is filled in.

Configuration caching is disabled by default because ModPublisher 2.2.3 retains Gradle `Project` objects in its upload tasks. Marking those tasks incompatible prevents a cache failure, but does not prevent Gradle from attempting serialization and reporting problems. Keep `--no-configuration-cache` on publishing commands, especially when an IDE or CI enables the cache. Ordinary builds can opt in with `.\gradlew.bat build --configuration-cache`; the build cache remains enabled either way.

The version number on Modrinth is `${mod_version}+mc${minecraft_version}`; the display name on both sites is `Create Train Automation ${mod_version} - NeoForge ${minecraft_version}`. Change `publish_changelog` if you want a different release-notes file. Edit the changelog for each release; the whole file is uploaded.

## Publish when ready

With both projects and credentials configured, explicitly disable debug mode to upload:

```powershell
.\gradlew.bat publishMod -Ppublish_debug=false --no-configuration-cache
```

To upload to one platform, or retry only the platform that failed:

```powershell
.\gradlew.bat publishModrinth -Ppublish_debug=false --no-configuration-cache
.\gradlew.bat publishCurseforge -Ppublish_debug=false --no-configuration-cache
```

If one platform succeeds and the other fails, retry the failed platform's task to avoid duplicating the successful upload. The existing Maven `publish` task is separate and does not upload to these sites. No GitHub release is configured.

For CI, expose the same token names as secrets and run the same commands. Project IDs can be supplied without editing the file using `-Pmodrinth_project_id=YOUR_ID` and `-Pcurseforge_project_id=YOUR_NUMERIC_ID`.
