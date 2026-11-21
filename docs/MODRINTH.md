# Modrinth and CurseForge publishing

A tag push sends AcceleratedDamage to GitHub, Modrinth and CurseForge. `.github/workflows/release.yml` holds one job, and that job calls `reusable-multiloader-release.yml` from `brainage04/MinecraftModConventions` at `v3.0.1`.

## The tag

Nothing but a tag push starts the workflow. The tag is `v` plus `mod_version` from `gradle.properties`. With `mod_version=1.0.0` you push `v1.0.0`. A tag that does not match stops the run in the preparation job, before anything is built or uploaded.

## What gets built

Preparation runs `./gradlew clean build` and then looks in `build/libs` for two files. The Fabric release is `accelerateddamage-1.0.0.jar`. The NeoForge release is the single file matching `accelerateddamage-neoforge-*.jar`. If that pattern finds nothing, or finds two files, the release fails.

Both files go onto the GitHub release. This repository passes no `release_name`, so the title is just the tag, `v1.0.0`. The body is the annotated tag message. When the tag has no message, GitHub's generated notes are used. A hyphen anywhere in the tag, as in `v1.0.0-rc1`, marks the release as a prerelease.

## Where it goes

Five jobs follow preparation, one per destination:

| Job | Publishes |
| --- | --- |
| `github` | the GitHub release with both JARs |
| `modrinth-fabric` | the Fabric JAR to Modrinth |
| `modrinth-neoforge` | the NeoForge JAR to the same Modrinth project |
| `curseforge-fabric` | the Fabric JAR to CurseForge |
| `curseforge-neoforge` | the NeoForge JAR to CurseForge |

Both Modrinth jobs write to one project. The Fabric job finds or creates it. The NeoForge job waits for that job and takes the project id from it. Modrinth ends up with two versions per release, `1.0.0` on the `fabric` loader and `1.0.0-neoforge` on the `neoforge` loader, and the different version numbers keep them apart. Each lists Minecraft `26.2`, which comes from `minecraft_version`.

CurseForge gets the slug `accelerated-damage` and the numeric id in the `CURSEFORGE_PROJECT_ID` variable.

## Settings

Everything below lives under the repository's Settings, Secrets and variables, Actions.

`MODRINTH_TOKEN` is a Modrinth personal access token. `CURSEFORGE_TOKEN` is a CurseForge API token. The GitHub release needs no secret of its own, because GitHub supplies `GITHUB_TOKEN` and the workflow passes it along.

`MODRINTH_PROJECT_ID` is optional and can stay empty. The Fabric job resolves the project from the slug rather than from this variable, creating it as a draft project when nothing is there, and the NeoForge job reuses the id it found. The slug is the `fabric.mod.json` id, `accelerateddamage`, unless `.modrinth/project.json` names another. `CURSEFORGE_PROJECT_ID` is required for CurseForge and holds the numeric project id.

## When a step is skipped

Without `MODRINTH_TOKEN` both Modrinth jobs log a notice and stop. The GitHub release still happens. Without `CURSEFORGE_TOKEN` the two CurseForge jobs do the same. A `CURSEFORGE_TOKEN` with an empty `CURSEFORGE_PROJECT_ID` is different: that job fails.

A Modrinth version number that already exists on the project is not uploaded again, because the job lists the project's versions first. A GitHub release that already exists for the tag is edited, and its assets are replaced, rather than created twice.

Project and icon synchronization is allowed to fail without stopping the job, but the version upload then fails too. The project id it needs is exported only when synchronization succeeded, so a failed sync leaves the stale project metadata in place and no new version on the project.

## Repository visibility

The pinned workflow never asks GitHub whether the repository is public. A private repository publishes to Modrinth and CurseForge the same way a public one does. Only the token and project id settings above decide whether a store job runs.

## What the Modrinth project says

The Fabric job refreshes the project on every release. The body is `README.md`. The title, licence and contact links come from `fabric/src/main/resources/fabric.mod.json`, and a link that is missing falls back to the GitHub repository URL. The summary is the GitHub repository description, then `.modrinth/project.json`, then the `fabric.mod.json` description. `.modrinth/project.json` sets the primary category `game-mechanics` and the additional category `utility`, and can also override the slug and the side support. The icon is `common/src/main/resources/assets/accelerateddamage/icon.png`.

Version dependencies come from the `depends`, `recommends`, `suggests`, `conflicts` and `breaks` blocks of `fabric.mod.json`, with each mod id read as a Modrinth slug. `minecraft`, `java` and `fabricloader` are dropped, which leaves `fabric-api` as the only one listed. To change one, add a `dependency_overrides` entry to `.modrinth/project.json` with `project_id`, `project_slug` or `dependency_type`, or drop it with `skip`.

`fabric.mod.json` sets `environment` to `*` and declares no client entrypoint, so the project is marked server-side only. The NeoForge version lists no dependencies at all, because this repository has no `.modrinth/neoforge-dependencies.json` for the NeoForge job to read.

To cut the tag, follow `docs/RELEASE.md`.
