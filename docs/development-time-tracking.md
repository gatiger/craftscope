# CraftScope Development Time Tracking

CraftScope uses a simple timestamped event log to track development time. The source of truth is `docs/development-worklog.csv`.

## Commands

Use these commands in the CraftScope development chat:

- `start` — begin a new work session and start counting time.
- `pause` — stop counting time temporarily.
- `resume` — continue counting after a pause.
- `stop` — end the current work session/day.
- `status` — report the current timer state plus today's, this week's, precisely tracked, historical estimated, and combined total time.
- `note <text>` — add a timestamped note without changing the timer state.

Optional aliases:

- `lunch out` = `pause`
- `lunch in` = `resume`

## State rules

The timer is state-aware so duplicate commands do not corrupt totals.

| Current state | Command | Result |
| --- | --- | --- |
| Stopped | `start` | Start a new session |
| Running | `start` | Ignore; already running |
| Running | `pause` | Pause at the current timestamp |
| Paused | `pause` | Ignore; already paused |
| Paused | `resume` | Resume at the current timestamp |
| Running | `resume` | Ignore; already running |
| Running | `stop` | Stop at the current timestamp |
| Paused | `stop` | Close the session without adding paused time |
| Stopped | `stop` | Ignore; already stopped |

Multiple `pause`/`resume` pairs and multiple complete `start`/`stop` sessions in the same day are valid.

## Corrections

Mistakes can be corrected later by editing the raw event log. Examples:

- `change yesterday's last pause to stop`
- `change yesterday's 12:10 pause to 12:25`
- `delete last resume`

If a day ends with `pause` and no work resumes later, that final `pause` can be changed to `stop` at the same timestamp. This does not change the counted hours; it simply closes the session cleanly.

## Time calculation

Only intervals in a running state count toward precisely tracked development time:

- `start` -> `pause` or `stop`
- `resume` -> `pause` or `stop`

Paused time is never counted.

Historical estimated time is stored separately as a `historical` row with `duration_minutes`. It is never mixed into today's or this week's precisely tracked totals.

All timestamps are recorded as ISO 8601 values in Eastern Time (`America/New_York`) including the UTC offset, so daylight-saving changes remain unambiguous.

## Work categories

The `category` column is optional. When useful, work can be grouped into these broad categories:

- Recipes / Production
- UI
- Project Tracking
- Integrations
- Fabric
- NeoForge
- Testing / Debugging
- Documentation
- Release

A blank category is valid. Categories are for reporting only and do not affect time calculations.

## Status reporting

`status` reports, when possible:

- Current state: Running, Paused, or Stopped
- Current session start time
- Active time in the current session
- Total active time today
- Total active time this week
- Precisely tracked CraftScope development time
- Historical estimated development time
- Combined development time

## Historical development baseline

CraftScope development predates this time-tracking system. GitHub shows the repository's initial commit on August 24, 2026 at 7:38:59 PM Eastern, followed shortly afterward by the initial multi-loader project setup.

For planning purposes, development completed before official timestamp tracking begins is recorded as:

**Historical estimated development: 25h 00m**

This estimate covers August 24-26, 2026. It is intentionally kept separate from precisely tracked time so future totals remain transparent.

Official timestamp-based tracking begins with the first `start` event on or after August 27, 2026.

## Source of truth

`docs/development-worklog.csv` is authoritative. Timer state is derived from the event log rather than assumed from chat history. Before recording a timer command, read the latest event state, apply the rules above, then append or correct the log as appropriate.
