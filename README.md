# Bulk Plan (Android)

A personal tracker for the 6-month muscle-building plan: daily schedule with reminders,
food diary with barcode scanning, workout logger with progression, progress charts,
Garmin sync, and AI coach check-ins (any provider) that propose plan changes you approve.

On first launch an intake questionnaire builds a plan request you can give to any AI;
its reply imports as your custom plan.

## Install
Every push to `main` builds the app on GitHub Actions. On your phone, open the
**latest** release on this repo, download `BulkPlan.apk`, and install it
(allow installs from your browser when Android asks). New builds install over
the old one and keep your data.

## Setup in the app (Settings)
- **Plan start date**: the Monday you begin.
- **AI coach**: pick Gemini, DeepSeek, OpenAI, OpenRouter, Mistral, Anthropic or any
  OpenAI-compatible service, paste your key, and tap Load models. Used for check-ins,
  meal estimates, label photos and building a plan from the intake.
- **Reminders**: tick which kinds of items notify you (meals, workouts, supplements…).
- **Optional tests**: untick DEXA or bloodwork to drop them from the plan.
- **Edit schedule**: Today › Edit, or Settings › Plan.
- **Garmin**: create a Personal API key at connect.garmin.com/app/settings/personalApiToken and paste it.
- **USDA key** (optional): free at api.data.gov, raises the food-search limit.
- **Restore backup**: accepts a backup from the web tracker too.

## Data sources
- Barcode lookups: Open Food Facts (no key)
- Food search: USDA FoodData Central
- Garmin Connect Personal API (pull only)
