# Git History Binary Optimization & Repository Maintenance Reference

This document provides technical documentation, audit findings, architectural constraints, and the procedure for removing historical binary artifacts from the REX Player Git history.

---

## 1. Overview & Project Status

- **Historical Object Database Size**: ~481 MiB (`size-pack`: 456.96 MiB across 17,999 Git objects).
- **Current Tracked Source Tree Size**: ~15–21 MiB (618 tracked source files).
- **Optimized Repository Size**: ~26 MiB (`size-pack`: 24.60 MiB across 18,644 Git objects) — **a 94.6% size reduction**.
- **Fresh Clone Transfer Size**: ~24 MiB (reduced from ~455 MiB).

### Status Record
The remote repository history on `origin/master` is intentionally preserved in its existing state.

**Technical Rationale**:
1. **Shallow Clones**: Developers, CI runners, and downstream packagers can use `git clone --depth 1`, which transfers only ~15–20 MB.
2. **Current Repository Hygiene**: No binary files have been committed for several hundred commits. The bloat is strictly historical and not an ongoing issue.
3. **Attribution & Verification Integrity**: Rewriting history changes all commit SHAs down the tree, which mathematically invalidates PGP/SSH cryptographic commit signatures. Preserving the original history keeps historical verified commit badges intact.
4. **Ecosystem Stability**: Avoiding force-pushes on `master` preserves existing commit permalinks in GitHub issues, pull requests, and releases, preventing disruption across contributor forks.

This document serves as the permanent specification and operational reference should repository history rewriting ever be executed.

---

## 2. Historical Blob Audit & Root Cause Analysis

Analysis via `git-filter-repo --analyze` indicates that **>98.7% of all packed storage** stems from two obsolete paths introduced in early commits:

```
Historical Binary Footprint by Extension:
.aar   : 384,426,688 bytes unpacked / 369,688,900 bytes packed (~370 MiB)
.apk   : 190,884,566 bytes unpacked /  81,300,224 bytes packed (~81 MiB)
```

### Path 1: Vendored Native Libraries (`app/libs/`)
- **Packed Size**: ~369.7 MiB
- **Files**:
  - `app/libs/mpv-android-lib-v0.0.1.aar` (unpacked ~216 MB, packed ~215 MB)
  - `app/libs/mpv-android-lib-v0.0.2.aar` (unpacked ~99.6 MB, packed ~86.3 MB)
  - `app/libs/mpv-android-lib-v0.0.3.aar` (unpacked ~68.4 MB, packed ~68.0 MB)
- **Context**: Vendored during early library updates (commit `10b8936c`).
- **Resolution**: Replaced in commit `4323477c` by a remote Maven dependency:
  ```toml
  # gradle/libs.versions.toml
  mpv-lib = "com.github.sfsakhawat999:mpvRex-libmpv:0.0.9"
  ```
  The latest application source code does not reference or package files from `app/libs/`.

### Path 2: Packaged Build Outputs (`app/preview/`)
- **Packed Size**: ~81.4 MiB
- **Files**:
  - `app/preview/app-universal-preview.apk` (~81 MB)
  - `app/preview/app-arm64-v8a-preview.apk` (~26 MB)
  - `app/preview/app-armeabi-v7a-preview.apk` (~25 MB)
  - `app/preview/app-x86-preview.apk` (~27 MB)
  - `app/preview/app-x86_64-preview.apk` (~28 MB)
  - `app/preview/baselineProfiles/*/*.dm` (~82 KB)
  - `app/preview/output-metadata.json` (~2 KB)
- **Context**: Accidentally tracked in commit `9854df82` and deleted from tracking in commit `37472712`.
- **Resolution**: Generated APK artifacts that should not exist in version control.

### Legitimate Source Assets
The removal policy must explicitly exempt source assets required by the application:
- Typography: `app/src/main/res/font/google_sans_flex.ttf` (3.9 MB) and font variants
- Store Metadata: `fastlane/metadata/android/en-US/images/phoneScreenshots/*.png`
- Application Icons: `app/src/main/ic_launcher-playstore.png`
- Build System Wrapper: `gradle/wrapper/gradle-wrapper.jar` (~58 KB)

---

## 3. Revision Numbering Architecture

The application relies on Git metadata to generate version identifiers (e.g., `r1038`) and determine preview updates.

### Build & Version Pipeline
1. **Commit Counting (`app/build.gradle.kts`)**:
   ```kotlin
   fun getCommitCount(): String =
     providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
       .standardOutput.asText.get().trim().ifEmpty { "0" }

   defaultConfig {
     buildConfigField("int", "GIT_COUNT", getCommitCount())
     buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")
   }

   buildTypes {
     named("debug") { versionNameSuffix = "-${getCommitCount()}" }
     create("preview") { versionNameSuffix = "-${getCommitCount()}" }
   }
   ```
2. **UI Presentation (`MainActivity.kt`)**:
   ```kotlin
   val currentVersion = if (UpdateManager.isPreviewBuild()) {
     val count = BuildConfig.GIT_COUNT.takeIf { it > 0 }
       ?: BuildConfig.VERSION_NAME.substringAfterLast('-', "").toIntOrNull()
       ?: 0
     "r$count"
   } else {
     BuildConfig.VERSION_NAME.replace("-dev", "")
   }
   ```
3. **In-App Update Comparison (`UpdateFeature.kt`)**:
   `getCurrentCommitCount()` inspects `BuildConfig.GIT_COUNT`.
   `isNewerPreviewVersion(remoteTag, currentCommitCount)` evaluates whether the remote release revision exceeds the local integer serial.
4. **CI Release Workflow (`.github/workflows/preview.yml`)**:
   Names artifacts using `REX-Player-preview-r${commit_count}-${arch}.apk` based on `git rev-list --count HEAD`.

### Critical Invariant: Empty Commit Retention
By default, history-rewriting tools prune commits that become empty once targeted files are removed.
- Pruning empty commits decrements `git rev-list --count HEAD`.
- A lower commit count breaks preview version comparisons on existing user installations.
- **Requirement**: Any history rewrite must pass `--prune-empty never --prune-degenerate never`. This preserves empty commits, original authorship metadata, timestamps, and commit ordering, maintaining the exact revision count.

---

## 4. History Rewriting Procedure

### 1. Preparation
Always perform history modifications in an isolated clone:
```bash
git clone --branch master <source-repo-url-or-path> REX-Player-rewritten
cd REX-Player-rewritten
```

### 2. Execution
Run `git-filter-repo` specifying unwanted paths and disabling empty commit pruning:
```bash
git-filter-repo \
  --path app/libs \
  --path app/preview \
  --invert-paths \
  --prune-empty never \
  --prune-degenerate never
```

### 3. Verification Protocol

- **Commit Count Verification**:
  ```bash
  git rev-list --count HEAD
  # Must match the baseline count before rewrite (e.g. 1038)
  ```

- **Tree SHA Invariant Check**:
  ```bash
  # Root tree SHA must be bit-for-bit identical to the unrewritten repository:
  git rev-parse HEAD^{tree}
  ```

- **Binary Blob Absence Check**:
  ```bash
  git log --all --name-only --oneline | grep -E "\.(aar|apk|dm)$"
  # Must return no matches
  ```

- **Object Database Size**:
  ```bash
  git count-objects -vH
  # size-pack should reflect ~24–27 MiB
  ```

- **Generated BuildConfig Verification**:
  ```bash
  ./gradlew generateDebugBuildConfig
  grep -E "GIT_COUNT|VERSION_NAME" app/build/generated/source/buildConfig/debug/xyz/mpv/rex/BuildConfig.java
  ```

---

## 5. Remote Deployment & Downstream Synchronization

### Upstream Push Procedure
If the rewrite is deployed to GitHub:
```bash
# Push rewritten master branch
git push origin master --force

# Push rewritten release tags
git push origin --tags --force
```

### Downstream Contributor Synchronization
Rewriting history creates a divergent commit graph. Existing local checkouts reject non-fast-forward updates.

To realign a downstream clone with the updated upstream history:
- **Clean tracking clones**:
  ```bash
  git fetch origin
  git reset --hard origin/master
  git reflog expire --expire=now --all
  git gc --prune=now --aggressive
  ```
- **Forks with unmerged feature branches**:
  ```bash
  git fetch origin
  git rebase --onto origin/master <divergence-base-sha> <feature-branch>
  ```

---

## 6. Artifact Prevention Rules

To ensure generated packages and binaries are not tracked in future commits, `.gitignore` should maintain rules for standard build output formats:

```gitignore
# Packaged binaries and build artifacts
*.apk
*.ap_
*.aab
*.aar
*.dm
app/preview/
```
