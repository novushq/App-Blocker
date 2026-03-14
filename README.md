# AppBlocker

A Compose-based Android app blocker with integrated usage dashboard.

## What is implemented

- 4-tab bottom navigation:
  - `Blocks`
  - `Usage`
  - `Reports` (placeholder)
  - `Settings` (placeholder)
- Blocks tab redesigned to show:
  - grouped blocked apps (not day-wise cards)
  - grouped blocked websites
  - detail sheets per app/website with rule toggle + delete
- Usage integration:
  - each blocked app card shows today's usage
  - app detail action from block sheet opens Usage drill-down for that package
- Usage dashboard redesigned with reusable chart/cards:
  - total screen time hero
  - hourly timeline chart
  - app list with progress bars
  - app-specific drill-down with sessions (latest -> oldest)
- Add restriction flow:
  - app or website target
  - day selector
  - custom start/end time (`HH:mm`)

## Key files

- `app/src/main/java/appblocker/appblocker/MainActivity.kt`
- `app/src/main/java/appblocker/appblocker/ui/screens/MainScreen.kt`
- `app/src/main/java/appblocker/appblocker/ui/screens/AppUsageScreen.kt`
- `app/src/main/java/appblocker/appblocker/ui/screens/ReportsScreen.kt`
- `app/src/main/java/appblocker/appblocker/ui/screens/SettingsScreen.kt`
- `app/src/main/java/appblocker/appblocker/ui/components/FGCards.kt`
- `app/src/main/java/appblocker/appblocker/ui/components/FGCharts.kt`
- `app/src/main/java/appblocker/appblocker/ui/theme/Color.kt`
- `app/src/main/java/appblocker/appblocker/ui/theme/Theme.kt`

## Run

```bash
./gradlew :app:assembleDebug
```

Install debug APK from:

- `app/build/outputs/apk/debug/app-debug.apk`

## Notes

- Usage stats require the user to grant Usage Access in system settings.
- Overlay permission is required for block overlay behavior.
- Accessibility permission is required for web protection flows.

