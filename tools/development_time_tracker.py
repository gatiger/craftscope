#!/usr/bin/env python3
"""State-aware helper for CraftScope's ODS development worklog.

The workbook layout is intentionally treated as a template:

    Row 1: merged title
    Row 2: headers
    Row 3+: development events
    Final row: "Total Time" in column E, total value in column F

New events are inserted immediately above the Total Time row. Because the total
row itself is moved down by the insertion, the user's existing formatting stays
intact.

The ODS file stores date/time cells using their native OpenDocument value types.
The visible time format remains HH:MM, while newly recorded events can still keep
seconds internally.

No third-party Python packages are required.
"""
from __future__ import annotations

import argparse
import copy
import os
import re
import tempfile
import zipfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Iterable

VALID_EVENTS = {"start", "pause", "resume", "stop", "note", "historical"}
STATE_EVENTS = {"start", "pause", "resume", "stop"}

NS_TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
NS_OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
NS_TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
NS_CALCEXT = "urn:org:documentfoundation:names:experimental:calc:xmlns:calcext:1.0"

T_TABLE = f"{{{NS_TABLE}}}table"
T_ROW = f"{{{NS_TABLE}}}table-row"
T_CELL = f"{{{NS_TABLE}}}table-cell"
T_P = f"{{{NS_TEXT}}}p"

A_COL_REPEAT = f"{{{NS_TABLE}}}number-columns-repeated"
A_ROW_REPEAT = f"{{{NS_TABLE}}}number-rows-repeated"
A_VALUE_TYPE = f"{{{NS_OFFICE}}}value-type"
A_DATE_VALUE = f"{{{NS_OFFICE}}}date-value"
A_TIME_VALUE = f"{{{NS_OFFICE}}}time-value"
A_VALUE = f"{{{NS_OFFICE}}}value"
A_CALCEXT_TYPE = f"{{{NS_CALCEXT}}}value-type"

VALUE_ATTRS = {
    A_VALUE_TYPE,
    A_DATE_VALUE,
    A_TIME_VALUE,
    A_VALUE,
    A_CALCEXT_TYPE,
}


@dataclass
class Event:
    timestamp: datetime
    event: str
    category: str = ""
    note: str = ""
    duration_minutes: int = 0


def register_namespaces(path: Path) -> None:
    """Preserve the document's existing namespace prefixes on rewrite."""
    seen: set[tuple[str, str]] = set()
    with zipfile.ZipFile(path, "r") as zf:
        content = zf.read("content.xml")

    with tempfile.NamedTemporaryFile(delete=False, suffix=".xml") as f:
        f.write(content)
        temp_name = f.name

    try:
        for _, item in ET.iterparse(temp_name, events=("start-ns",)):
            prefix, uri = item
            pair = (prefix or "", uri)
            if pair in seen:
                continue
            seen.add(pair)
            try:
                ET.register_namespace(prefix or "", uri)
            except ValueError:
                # Reserved prefixes are already understood by ElementTree.
                pass
    finally:
        os.unlink(temp_name)


def load_content(path: Path) -> tuple[ET.ElementTree, ET.Element]:
    register_namespaces(path)
    with zipfile.ZipFile(path, "r") as zf:
        raw = zf.read("content.xml")
    root = ET.fromstring(raw)
    return ET.ElementTree(root), root


def get_sheet(root: ET.Element) -> ET.Element:
    table = root.find(f".//{T_TABLE}")
    if table is None:
        raise RuntimeError("No worksheet was found in the ODS file")
    return table


def cell_text(cell: ET.Element) -> str:
    return "".join(cell.itertext()).strip()


def logical_cells(row: ET.Element, limit: int = 6) -> list[ET.Element]:
    cells: list[ET.Element] = []
    for cell in row.findall(T_CELL):
        repeat = int(cell.get(A_COL_REPEAT, "1"))
        for _ in range(repeat):
            cells.append(cell)
            if len(cells) >= limit:
                return cells
    return cells


def find_total_row(sheet: ET.Element) -> ET.Element:
    for row in sheet.findall(T_ROW):
        cells = logical_cells(row, 6)
        if len(cells) >= 5 and cell_text(cells[4]).strip().lower() == "total time":
            return row
    raise RuntimeError('Could not find the "Total Time" row')


def data_rows(sheet: ET.Element, total_row: ET.Element) -> list[ET.Element]:
    rows: list[ET.Element] = []
    for row in sheet.findall(T_ROW):
        if row is total_row:
            break
        cells = logical_cells(row, 6)
        if len(cells) < 3:
            continue
        event = cell_text(cells[2]).strip().lower()
        if event in VALID_EVENTS:
            rows.append(row)
    return rows


def parse_ods_time(value: str) -> tuple[int, int, int]:
    match = re.fullmatch(
        r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?",
        value or "",
    )
    if not match:
        return (0, 0, 0)
    hours = int(match.group(1) or 0)
    minutes = int(match.group(2) or 0)
    seconds = int(float(match.group(3) or 0))
    return hours, minutes, seconds


def row_to_event(row: ET.Element) -> Event | None:
    cells = logical_cells(row, 6)
    if len(cells) < 6:
        return None

    event_name = cell_text(cells[2]).strip().lower()
    if event_name not in VALID_EVENTS:
        return None

    date_value = cells[0].get(A_DATE_VALUE, "").strip()
    time_value = cells[1].get(A_TIME_VALUE, "").strip()

    if date_value:
        date_part = datetime.fromisoformat(date_value).date()
    else:
        raw_date = cell_text(cells[0]).strip()
        date_part = datetime.strptime(raw_date, "%m/%d/%y").date()

    if time_value:
        hour, minute, second = parse_ods_time(time_value)
    else:
        raw_time = cell_text(cells[1]).strip()
        parsed_time = datetime.strptime(raw_time, "%H:%M")
        hour, minute, second = parsed_time.hour, parsed_time.minute, 0

    timestamp = datetime(
        date_part.year,
        date_part.month,
        date_part.day,
        hour,
        minute,
        second,
    )

    raw_duration = cells[5].get(A_VALUE, "").strip() or cell_text(cells[5]).strip()
    try:
        duration_minutes = int(float(raw_duration)) if raw_duration else 0
    except ValueError:
        duration_minutes = 0

    return Event(
        timestamp=timestamp,
        event=event_name,
        category=cell_text(cells[3]).strip(),
        note=cell_text(cells[4]).strip(),
        duration_minutes=max(0, duration_minutes),
    )


def read_events(path: Path) -> list[Event]:
    _, root = load_content(path)
    sheet = get_sheet(root)
    total = find_total_row(sheet)

    events: list[Event] = []
    for row in data_rows(sheet, total):
        event = row_to_event(row)
        if event is not None:
            events.append(event)

    events.sort(key=lambda e: e.timestamp)
    return events


def current_state(events: list[Event]) -> str:
    for event in reversed(events):
        if event.event not in STATE_EVENTS:
            continue
        if event.event in {"start", "resume"}:
            return "running"
        if event.event == "pause":
            return "paused"
        return "stopped"
    return "stopped"


def active_intervals(
    events: list[Event],
    as_of: datetime,
) -> list[tuple[datetime, datetime]]:
    intervals: list[tuple[datetime, datetime]] = []
    running_since: datetime | None = None
    state = "stopped"

    for event in events:
        if event.timestamp > as_of or event.event in {"note", "historical"}:
            continue

        if event.event == "start" and state == "stopped":
            running_since = event.timestamp
            state = "running"

        elif event.event == "resume" and state == "paused":
            running_since = event.timestamp
            state = "running"

        elif event.event in {"pause", "stop"} and state == "running":
            if running_since is not None and event.timestamp >= running_since:
                intervals.append((running_since, event.timestamp))
            running_since = None
            state = "paused" if event.event == "pause" else "stopped"

        elif event.event == "stop" and state == "paused":
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


def historical_duration(
    events: Iterable[Event],
    as_of: datetime | None = None,
) -> timedelta:
    minutes = 0
    for event in events:
        if event.event != "historical":
            continue
        if as_of is not None and event.timestamp > as_of:
            continue
        minutes += event.duration_minutes
    return timedelta(minutes=minutes)


def combined_duration(events: list[Event], as_of: datetime) -> timedelta:
    return (
        clipped_duration(active_intervals(events, as_of))
        + historical_duration(events, as_of)
    )


def session_start(events: list[Event]) -> datetime | None:
    last_stop = -1
    for index, event in enumerate(events):
        if event.event == "stop":
            last_stop = index
    for event in events[last_stop + 1 :]:
        if event.event == "start":
            return event.timestamp
    return None


def format_duration(value: timedelta) -> str:
    seconds = max(0, int(value.total_seconds()))
    hours, remainder = divmod(seconds, 3600)
    minutes, _ = divmod(remainder, 60)
    return f"{hours}h {minutes:02d}m"


def clear_value(cell: ET.Element) -> None:
    for attr in list(cell.attrib):
        if attr in VALUE_ATTRS or attr == A_COL_REPEAT:
            cell.attrib.pop(attr, None)
    for child in list(cell):
        cell.remove(child)


def set_text(cell: ET.Element, value: str) -> None:
    clear_value(cell)
    cell.set(A_VALUE_TYPE, "string")
    cell.set(A_CALCEXT_TYPE, "string")
    p = ET.SubElement(cell, T_P)
    p.text = value


def set_date(cell: ET.Element, value: datetime) -> None:
    clear_value(cell)
    cell.set(A_VALUE_TYPE, "date")
    cell.set(A_DATE_VALUE, value.strftime("%Y-%m-%d"))
    cell.set(A_CALCEXT_TYPE, "date")
    p = ET.SubElement(cell, T_P)
    p.text = value.strftime("%m/%d/%y")


def set_time(cell: ET.Element, value: datetime) -> None:
    clear_value(cell)
    cell.set(A_VALUE_TYPE, "time")
    cell.set(
        A_TIME_VALUE,
        f"PT{value.hour:02d}H{value.minute:02d}M{value.second:02d}S",
    )
    cell.set(A_CALCEXT_TYPE, "time")
    p = ET.SubElement(cell, T_P)
    # The workbook's visible format is HH:MM.
    p.text = value.strftime("%H:%M")


def set_number(cell: ET.Element, value: int) -> None:
    clear_value(cell)
    cell.set(A_VALUE_TYPE, "float")
    cell.set(A_VALUE, str(value))
    cell.set(A_CALCEXT_TYPE, "float")
    p = ET.SubElement(cell, T_P)
    p.text = str(value)


def make_event_row(template_row: ET.Element, event: Event) -> ET.Element:
    row = copy.deepcopy(template_row)
    row.attrib.pop(A_ROW_REPEAT, None)

    cells = logical_cells(row, 6)
    if len(cells) < 6:
        raise RuntimeError("The worklog template row does not contain six columns")

    # The normal event rows in the supplied template already have six
    # independent cells. Refuse an unexpected repeated cell here rather
    # than risking a malformed row.
    if len({id(cell) for cell in cells}) != 6:
        raise RuntimeError(
            "The selected template row contains repeated cells; "
            "add at least one normal event row before using the tracker"
        )

    set_date(cells[0], event.timestamp)
    set_time(cells[1], event.timestamp)
    set_text(cells[2], event.event)
    set_text(cells[3], event.category)
    set_text(cells[4], event.note)

    if event.event == "historical":
        set_number(cells[5], event.duration_minutes)
    else:
        clear_value(cells[5])

    return row


def update_total_row(
    total_row: ET.Element,
    value: timedelta,
) -> None:
    cells = logical_cells(total_row, 6)
    if len(cells) < 6:
        raise RuntimeError("The Total Time row does not contain six columns")
    set_text(cells[5], format_duration(value))


def write_ods(
    path: Path,
    tree: ET.ElementTree,
) -> None:
    xml_bytes = ET.tostring(
        tree.getroot(),
        encoding="utf-8",
        xml_declaration=True,
    )

    temp_fd, temp_name = tempfile.mkstemp(
        suffix=".ods",
        dir=str(path.parent),
    )
    os.close(temp_fd)

    try:
        with zipfile.ZipFile(path, "r") as src, zipfile.ZipFile(
            temp_name,
            "w",
        ) as dst:
            names = src.namelist()

            # The ODS specification expects mimetype to be first and stored.
            if "mimetype" in names:
                info = src.getinfo("mimetype")
                mimetype_info = copy.copy(info)
                mimetype_info.compress_type = zipfile.ZIP_STORED
                dst.writestr(mimetype_info, src.read("mimetype"))

            for info in src.infolist():
                if info.filename == "mimetype":
                    continue
                data = (
                    xml_bytes
                    if info.filename == "content.xml"
                    else src.read(info.filename)
                )
                dst.writestr(info, data)

        os.replace(temp_name, path)

    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def append_event(
    path: Path,
    event: Event,
) -> tuple[bool, str]:
    tree, root = load_content(path)
    sheet = get_sheet(root)
    total_row = find_total_row(sheet)
    rows = data_rows(sheet, total_row)

    events: list[Event] = []
    for row in rows:
        parsed = row_to_event(row)
        if parsed is not None:
            events.append(parsed)
    events.sort(key=lambda e: e.timestamp)

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

    if not rows:
        raise RuntimeError("No existing data row is available as a formatting template")

    # Prefer the most recent ordinary event row. The supplied workbook already
    # has one; this keeps the date/time/body styles exactly as the user designed.
    template_row = rows[-1]
    new_row = make_event_row(template_row, event)

    children = list(sheet)
    total_index = children.index(total_row)
    sheet.insert(total_index, new_row)

    events.append(event)
    events.sort(key=lambda e: e.timestamp)

    update_total_row(
        total_row,
        combined_duration(events, event.timestamp),
    )

    write_ods(path, tree)
    return True, "recorded"


def status(
    path: Path,
    as_of: datetime,
) -> str:
    events = read_events(path)
    visible_events = [
        event
        for event in events
        if event.timestamp <= as_of
    ]

    intervals = active_intervals(events, as_of)
    state = current_state(visible_events)

    day_start = as_of.replace(
        hour=0,
        minute=0,
        second=0,
        microsecond=0,
    )
    day_end = day_start + timedelta(days=1)
    week_start = day_start - timedelta(days=day_start.weekday())
    week_end = week_start + timedelta(days=7)

    start = session_start(visible_events)
    session_total = timedelta()

    if start:
        session_total = clipped_duration(
            intervals,
            start=start,
            end=as_of,
        )

    today = clipped_duration(
        intervals,
        day_start,
        day_end,
    )
    this_week = clipped_duration(
        intervals,
        week_start,
        week_end,
    )
    tracked = clipped_duration(intervals)
    historical = historical_duration(events, as_of)
    combined = tracked + historical

    lines = [f"State: {state.upper()}"]

    if start and state != "stopped":
        lines.append(
            f"Current session started: {start.isoformat()}"
        )
        lines.append(
            f"Current session active: {format_duration(session_total)}"
        )

    lines.append(f"Today: {format_duration(today)}")
    lines.append(f"This week: {format_duration(this_week)}")
    lines.append(f"Precisely tracked: {format_duration(tracked)}")
    lines.append(f"Historical estimate: {format_duration(historical)}")
    lines.append(f"Combined total: {format_duration(combined)}")

    return "\n".join(lines)


def change_last_pause_to_stop(
    path: Path,
) -> tuple[bool, str]:
    tree, root = load_content(path)
    sheet = get_sheet(root)
    total_row = find_total_row(sheet)
    rows = data_rows(sheet, total_row)

    parsed_rows: list[tuple[ET.Element, Event]] = []
    for row in rows:
        event = row_to_event(row)
        if event is not None:
            parsed_rows.append((row, event))

    for row, event in reversed(parsed_rows):
        if event.event in {"note", "historical"}:
            continue

        if event.event != "pause":
            return False, f"last state event is {event.event}, not pause"

        cells = logical_cells(row, 6)
        set_text(cells[2], "stop")
        event.event = "stop"

        events = [item[1] for item in parsed_rows]
        events.sort(key=lambda e: e.timestamp)

        update_total_row(
            total_row,
            combined_duration(events, event.timestamp),
        )

        write_ods(path, tree)
        return True, "changed last pause to stop"

    return False, "no state event found"


def local_wall_time(value: str) -> datetime:
    """Keep the wall-clock part of the explicit offset-aware timestamp."""
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        raise ValueError(
            "--at must include a UTC offset, for example "
            "2026-08-28T08:15:00-04:00"
        )
    return parsed.replace(tzinfo=None)



def refresh_total(
    path: Path,
) -> str:
    tree, root = load_content(path)
    sheet = get_sheet(root)
    total_row = find_total_row(sheet)

    events: list[Event] = []
    for row in data_rows(sheet, total_row):
        event = row_to_event(row)
        if event is not None:
            events.append(event)

    if events:
        as_of = max(event.timestamp for event in events)
    else:
        as_of = datetime.now()

    update_total_row(
        total_row,
        combined_duration(events, as_of),
    )

    write_ods(path, tree)
    return format_duration(
        combined_duration(events, as_of)
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--log",
        default="docs/development-worklog.ods",
    )

    sub = parser.add_subparsers(
        dest="command",
        required=True,
    )

    for name in ("start", "pause", "resume", "stop"):
        command = sub.add_parser(name)
        command.add_argument(
            "--at",
            required=True,
            help="Offset-aware ISO-8601 timestamp",
        )
        command.add_argument(
            "--category",
            default="Development",
        )
        command.add_argument(
            "--note",
            default="",
        )

    command = sub.add_parser("note")
    command.add_argument("text")
    command.add_argument("--at", required=True)
    command.add_argument("--category", default="Development")

    command = sub.add_parser("historical")
    command.add_argument(
        "--minutes",
        required=True,
        type=int,
    )
    command.add_argument("--at", required=True)
    command.add_argument("--category", default="Historical")
    command.add_argument("--note", default="")

    command = sub.add_parser("status")
    command.add_argument("--at", required=True)

    sub.add_parser("change-last-pause-to-stop")
    sub.add_parser("refresh-total")

    args = parser.parse_args()
    path = Path(args.log)

    if not path.exists():
        raise SystemExit(
            f"Worklog not found: {path}"
        )

    if args.command == "status":
        print(
            status(
                path,
                local_wall_time(args.at),
            )
        )
        return

    if args.command == "change-last-pause-to-stop":
        ok, message = change_last_pause_to_stop(path)
        print(message)
        raise SystemExit(0 if ok else 1)

    if args.command == "refresh-total":
        print(
            "Total Time:",
            refresh_total(path),
        )
        return

    if args.command == "note":
        event = Event(
            local_wall_time(args.at),
            "note",
            args.category,
            args.text,
        )

    elif args.command == "historical":
        event = Event(
            local_wall_time(args.at),
            "historical",
            args.category,
            args.note,
            max(0, args.minutes),
        )

    else:
        event = Event(
            local_wall_time(args.at),
            args.command,
            args.category,
            args.note,
        )

    ok, message = append_event(path, event)
    print(message)

    if not ok:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
