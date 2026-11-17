# Bulk Plan (Android)

A personal tracker for the 6-month muscle-building plan: daily schedule with reminders,
food diary with barcode scanning, workout logger with progression, progress charts,
Garmin sync, and Claude check-ins that propose plan changes you approve.

## Install
Every push to `main` builds the app on GitHub Actions. On your phone, open the
**latest** release on this repo, download `BulkPlan.apk`, and install it
(allow installs from your browser when Android asks). New builds install over
the old one and keep your data.

## Setup in the app (Settings)
- **Plan start date**: the Monday you begin.
- **Claude API key**: from console.anthropic.com. Used for check-ins, meal estimates and label photos.
- **Garmin**: create a Personal API key at connect.garmin.com/app/settings/personalApiToken and paste it.
- **USDA key** (optional): free at api.data.gov, raises the food-search limit.
- **Restore backup**: accepts a backup from the web tracker too.

## Data sources
- Barcode lookups: Open Food Facts (no key)
- Food search: USDA FoodData Central
- Garmin Connect Personal API (pull only)
