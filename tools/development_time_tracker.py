#!/usr/bin/env python3
"""State-aware helper for CraftScope's timestamped development worklog.

The chat should supply an explicit ISO-8601 timestamp with --at for commands that
record or calculate current time. This keeps the log independent of the machine's
system clock and makes timezone handling explicit.

Historical estimates are stored as `historical` rows with duration_minutes and
are reported separately from precisely tracked time.
"""
from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Iterable

VALID_EVENTS = {"start", "pause", "resume", "stop", "note", "historical"}
STATE_EVENTS = {"start", "pause", "resume", "stop"}


@dataclass
class Event:
    timestamp: datetime
    event: str
    category: str = ""
    note: str = ""
    duration_minutes: int = 0


def read_events(path: Path) -> list[Event]:
    if not path.exists():
        return []
    events: list[Event] = []
    with path.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            ts = (row.get("timestamp") or "").strip()
            event = (row.get("event") or "").strip().lower()
            if not ts or event not in VALID_EVENTS:
                continue
            raw_minutes = (row.get("duration_minutes") or "").strip()
            try:
                duration_minutes = int(raw_minutes) if raw_minutes else 0
            except ValueError:
                duration_minutes = 0
            events.append(
                Event(
                    datetime.fromisoformat(ts),
                    event,
                    (row.get("category") or "").strip(),
                    (row.get("note") or "").strip(),
                    max(0, duration_minutes),
                )
            )
    events.sort(key=lambda e: e.timestamp)
    return events


def write_events(path: Path, events: Iterable[Event]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["timestamp", "event", "category", "note", "duration_minutes"])
        for e in events:
            writer.writerow(
                [
                    e.timestamp.isoformat(),
                    e.event,
                    e.category,
                    e.note,
                    e.duration_minutes if e.event == "historical" else "",
                ]
            )


def current_state(events: list[Event]) -> str:
    for e in reversed(events):
        if e.event not in STATE_EVENTS:
            continue
        if e.event in {"start", "resume"}:
            return "running"
        if e.event == "pause":
            return "paused"
        return "stopped"
    return "stopped"


def append_event(path: Path, event: Event) -> tuple[bool, str]:
    events = read_events(path)
    state = current_state(events)

    allowed = {
        "start": state == "stopped",
        "pause": state == "running",
        "resume": state == "paused",
        "stop": state in {"running", "paused"},
        "note": True,
        "historical": True,
    }
    if not allowed[event.event]:
        return False, f"ignored: state is already {state}"

    events.append(event)
    write_events(path, events)
    return True, "recorded"


def active_intervals(events: list[Event], as_of: datetime) -> list[tuple[datetime, datetime]]:
    intervals: list[tuple[datetime, datetime]] = []
    running_since: datetime | None = None
    state = "stopped"

    for e in events:
        if e.timestamp > as_of or e.event in {"note", "historical"}:
            continue
        if e.event == "start" and state == "stopped":
            running_since = e.timestamp
            state = "running"
        elif e.event == "resume" and state == "paused":
            running_since = e.timestamp
            state = "running"
        elif e.event in {"pause", "stop"} and state == "running":
            if running_since is not None and e.timestamp >= running_since:
                intervals.append((running_since, e.timestamp))
            running_since = None
            state = "paused" if e.event == "pause" else "stopped"
        elif e.event == "stop" and state == "paused":
            state = "stopped"

    if state == "running" and running_since is not None and as_of >= running_since:
        intervals.append((running_since, as_of))
    return intervals


def clipped_duration(
    intervals: Iterable[tuple[datetime, datetime]],
    start: datetime | None = None,
    end: datetime | None = None,
) -> timedelta:
    total = timedelta()
    for a, b in intervals:
        x = max(a, start) if start else a
        y = min(b, end) if end else b
        if y > x:
            total += y - x
    return total


def historical_duration(events: Iterable[Event], as_of: datetime | None = None) -> timedelta:
    minutes = 0
    for e in events:
        if e.event != "historical":
            continue
        if as_of is not None and e.timestamp > as_of:
            continue
        minutes += e.duration_minutes
    return timedelta(minutes=minutes)


def session_start(events: list[Event]) -> datetime | None:
    last_stop = -1
    for i, e in enumerate(events):
        if e.event == "stop":
            last_stop = i
    for e in events[last_stop + 1:]:
        if e.event == "start":
            return e.timestamp
    return None


def format_duration(value: timedelta) -> str:
    seconds = max(0, int(value.total_seconds()))
    hours, rem = divmod(seconds, 3600)
    minutes, _ = divmod(rem, 60)
    return f"{hours}h {minutes:02d}m"


def status(path: Path, as_of: datetime) -> str:
    events = read_events(path)
    visible_events = [e for e in events if e.timestamp <= as_of]
    intervals = active_intervals(events, as_of)
    state = current_state(visible_events)

    day_start = as_of.replace(hour=0, minute=0, second=0, microsecond=0)
    day_end = day_start + timedelta(days=1)
    week_start = day_start - timedelta(days=day_start.weekday())
    week_end = week_start + timedelta(days=7)

    start = session_start(visible_events)
    session_total = timedelta()
    if start:
        session_total = clipped_duration(intervals, start=start, end=as_of)

    today = clipped_duration(intervals, day_start, day_end)
    this_week = clipped_duration(intervals, week_start, week_end)
    tracked = clipped_duration(intervals)
    historical = historical_duration(events, as_of)
    combined = tracked + historical

    lines = [f"State: {state.upper()}"]
    if start and state != "stopped":
        lines.append(f"Current session started: {start.isoformat()}")
        lines.append(f"Current session active: {format_duration(session_total)}")
    lines.append(f"Today: {format_duration(today)}")
    lines.append(f"This week: {format_duration(this_week)}")
    lines.append(f"Precisely tracked: {format_duration(tracked)}")
    lines.append(f"Historical estimate: {format_duration(historical)}")
    lines.append(f"Combined total: {format_duration(combined)}")
    return "\n".join(lines)


def change_last_pause_to_stop(path: Path) -> tuple[bool, str]:
    events = read_events(path)
    for e in reversed(events):
        if e.event in {"note", "historical"}:
            continue
        if e.event != "pause":
            return False, f"last state event is {e.event}, not pause"
        e.event = "stop"
        write_events(path, events)
        return True, "changed last pause to stop"
    return False, "no state event found"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", default="docs/development-worklog.csv")
    sub = parser.add_subparsers(dest="command", required=True)

    for name in ("start", "pause", "resume", "stop"):
        p = sub.add_parser(name)
        p.add_argument("--at", required=True, help="Offset-aware ISO-8601 timestamp")
        p.add_argument("--category", default="")
        p.add_argument("--note", default="")

    p = sub.add_parser("note")
    p.add_argument("text")
    p.add_argument("--at", required=True)
    p.add_argument("--category", default="")

    p = sub.add_parser("historical")
    p.add_argument("--minutes", required=True, type=int)
    p.add_argument("--at", required=True)
    p.add_argument("--category", default="Historical")
    p.add_argument("--note", default="")

    p = sub.add_parser("status")
    p.add_argument("--at", required=True)

    sub.add_parser("change-last-pause-to-stop")

    args = parser.parse_args()
    path = Path(args.log)

    if args.command == "status":
        print(status(path, datetime.fromisoformat(args.at)))
        return
    if args.command == "change-last-pause-to-stop":
        ok, message = change_last_pause_to_stop(path)
        print(message)
        raise SystemExit(0 if ok else 1)

    if args.command == "note":
        event = Event(datetime.fromisoformat(args.at), "note", args.category, args.text)
    elif args.command == "historical":
        event = Event(
            datetime.fromisoformat(args.at),
            "historical",
            args.category,
            args.note,
            max(0, args.minutes),
        )
    else:
        event = Event(datetime.fromisoformat(args.at), args.command, args.category, args.note)

    ok, message = append_event(path, event)
    print(message)
    if not ok:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
