# Update App Version to v2.2.8 and Add New Features Documentation

This plan outlines the steps to update the application version to 2.2.8 and document the new features in the `README.md` file.

## User Review Required

> [!NOTE]
> The version code in `app/build.gradle.kts` will be incremented from 2 to 3.

## Proposed Changes

### Build Configuration

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/NIGHTHAWK/Downloads/Telegram%20Desktop/TG-media-Backup--2.2.7/app/build.gradle.kts)
- Update `versionCode` to `3`.
- Update `versionName` to `"2.2.8"`.

---

### User Interface

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/NIGHTHAWK/Downloads/Telegram%20Desktop/TG-media-Backup--2.2.7/app/src/main/java/com/dparadox/tgbackup/ui/screens/SettingsScreen.kt)
- Update the hardcoded version string in the "About" section from `"2.2.7"` to `"2.2.8"`.

---

### Documentation

#### [MODIFY] [README.md](file:///C:/Users/NIGHTHAWK/Downloads/Telegram%20Desktop/TG-media-Backup--2.2.7/README.md)
- Update the version in the title from `v2.2.7` to `v2.2.8`.
- Replace the "What's New in v2.2.7" section with "What's New in v2.2.8" and include the following features:
    - **Restore Preview**: Confirmation dialog for "Restore All" showing the exact file count.
    - **Quiet-Hours Backup Window**: Restriction of scheduled backups to specific time ranges via Settings.
    - **Duplicate Finder**: Content-hash based scan for duplicate media in the Gallery.
    - **14-Day Activity Chart**: Bar chart of backup activity in the History screen.

## Verification Plan

### Automated Tests
- Run `gradlew help` to ensure the build configuration is still valid.
- (Optional) Run `app:assembleDebug` to verify the project still builds.

### Manual Verification
- The user should verify the version number in the Settings -> About section after deployment.
- The user should verify the updated `README.md` file content.
