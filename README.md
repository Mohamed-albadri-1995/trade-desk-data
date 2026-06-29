# Trade Desk — Backup Data

Auto-generated daily backups from the Trade Desk server.

- **Branch:** `fresh`
- **Schedule:** Every weekday at 5:30 PM ET (1 hour after market close)
- **Format:** `backups/YYYY-MM-DD.json` + `backups/latest.json`

Each backup contains: settings, shortlist, R1 frozen screener, R2 market snapshots, R3a/R3b registers.
