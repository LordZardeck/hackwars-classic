#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import subprocess
import sys
import xml.etree.ElementTree as ET


SCRIPT_FILE_TYPES = {
    0,
    1,
    2,
    3,
    4,
    5,
    6,
    7,
    12,
    15,
    23,
    24,
}

TEXT_FILE_TYPE = 9
CLUE_FILE_TYPE = 16
BOUNTY_FILE_TYPE = 17
QUEST_GAME_FILE_TYPE = 22
CHALLENGE_FILE_TYPE = 27
NEW_FIREWALL_FILE_TYPE = 28


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Backfill hackwars.user.stats XML rows into stats_json + user_stats_text_blob without touching user.stats.",
    )
    parser.add_argument("--host", default=os.environ.get("HACKWARS_DB_HOST", "127.0.0.1"))
    parser.add_argument("--port", default=os.environ.get("HACKWARS_DB_PORT", "3306"))
    parser.add_argument("--user", default=os.environ.get("HACKWARS_DB_USER", "root"))
    parser.add_argument("--password", default=os.environ.get("HACKWARS_DB_PASSWORD", ""))
    parser.add_argument("--database", default=os.environ.get("HACKWARS_DB_NAME", "hackwars"))
    parser.add_argument("--user-num", type=int)
    parser.add_argument("--start-num", type=int)
    parser.add_argument("--end-num", type=int)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--overwrite-existing-json", action="store_true")
    return parser.parse_args()


def mysql_base_command(args: argparse.Namespace) -> list[str]:
    return [
        "mysql",
        "--batch",
        "--raw",
        "--skip-column-names",
        "--host",
        str(args.host),
        "--port",
        str(args.port),
        "--user",
        str(args.user),
        str(args.database),
    ]


def run_mysql_query(args: argparse.Namespace, sql: str) -> str:
    env = os.environ.copy()
    env["MYSQL_PWD"] = args.password
    result = subprocess.run(
        mysql_base_command(args) + ["-e", sql],
        text=True,
        capture_output=True,
        env=env,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "mysql query failed")
    return result.stdout


def execute_mysql_script(args: argparse.Namespace, sql: str) -> None:
    env = os.environ.copy()
    env["MYSQL_PWD"] = args.password
    result = subprocess.run(
        mysql_base_command(args),
        text=True,
        input=sql,
        capture_output=True,
        env=env,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "mysql script failed")


def blob_field(path: str, kind: str, value: str | None, blobs: list[dict]) -> dict | None:
    if value is None:
        return None
    blobs.append({"path": path, "kind": kind, "text_content": value})
    return {"blobRef": {"path": path, "kind": kind}}


def inline_field(value: str | None) -> dict | None:
    if value is None:
        return None
    return {"inlineValue": value}


def child_text(parent: ET.Element | None, name: str, default: str | None = None) -> str | None:
    if parent is None:
        return default
    child = parent.find(name)
    if child is None:
        return default
    value = "".join(child.itertext())
    return value if value != "" else default


def parse_int(value: str | None, default: int = 0) -> int:
    try:
        return int(value) if value is not None else default
    except Exception:
        return default


def parse_float(value: str | None, default: float = 0.0) -> float:
    try:
        return float(value) if value is not None else default
    except Exception:
        return default


def parse_bool(value: str | None) -> bool:
    return str(value).strip() in {"1", "true", "True"}


def parse_known_or_default(parent: ET.Element, name: str, default):
    text = child_text(parent, name)
    if isinstance(default, int):
        return parse_int(text, default)
    if isinstance(default, float):
        return parse_float(text, default)
    return text if text is not None else default


def program_keys_for_port_type(port_type: int) -> list[str]:
    if port_type == 0:
        return ["deposit", "withdraw", "transfer"]
    if port_type in {2, 4}:
        return ["initialize", "continue", "finalize"]
    if port_type == 1:
        return ["get", "put"]
    if port_type == 3:
        return ["enter", "exit", "submit"]
    return []


def file_type_keys(file_type: int) -> list[str]:
    if file_type in {0, 1}:
        return ["deposit", "withdraw", "transfer"]
    if file_type in {2, 3, 23, 24}:
        return ["initialize", "finalize", "continue"]
    if file_type in {4, 5}:
        return ["fire"]
    if file_type in {6, 7}:
        return ["put", "get"]
    if file_type in {12, 15}:
        return ["enter", "exit", "submit"]
    if file_type == CLUE_FILE_TYPE:
        return ["currentstep", "cluelevel", "step0", "step1", "step2", "step3", "step4", "step5"]
    if file_type == BOUNTY_FILE_TYPE:
        return ["count", "script", "maker", "type", "reward", "target", "bountyip", "timeout"]
    if file_type in {18, 19}:
        return ["attribute0", "attribute1", "attribute2", "quality0", "quality1", "quality2", "timeout", "maxquality", "currentquality", "lastdegrade"]
    if file_type == 25:
        return ["questid", "itemname", "imageid"]
    if file_type == CHALLENGE_FILE_TYPE:
        return ["input", "output", "inputtype", "outputtype", "task", "questid", "identifier"]
    if file_type == QUEST_GAME_FILE_TYPE:
        return ["data", "level", "questid", "task"]
    if file_type == 29:
        return ["itemname", "imageid"]
    if file_type == NEW_FIREWALL_FILE_TYPE:
        return [
            "bank_damage_modifier",
            "attack_damage_modifier",
            "redirect_damage_modifier",
            "ftp_damage_modifier",
            "http_damage_modifier",
            "specialAttribute1",
            "specialAttribute2",
            "attack_damage",
            "equip_level",
            "name",
            "store_price",
        ]
    return ["data", "level"]


def should_blob_file_content(file_type: int, key: str, special_key: str | None = None) -> bool:
    if file_type in SCRIPT_FILE_TYPES:
        return True
    if file_type == TEXT_FILE_TYPE and key == "data":
        return True
    if file_type == CLUE_FILE_TYPE and key.startswith("step"):
        return True
    if file_type == BOUNTY_FILE_TYPE and key == "script":
        return True
    if file_type == CHALLENGE_FILE_TYPE and key in {"input", "output", "task"}:
        return True
    if file_type == QUEST_GAME_FILE_TYPE and key in {"data", "task"}:
        return True
    if file_type == NEW_FIREWALL_FILE_TYPE and key in {"specialAttribute1", "specialAttribute2"} and special_key in {"long_desc", "short_desc"}:
        return True
    return False


def file_blob_kind(file_type: int, key: str, special_key: str | None = None) -> str:
    if file_type == TEXT_FILE_TYPE and key == "data":
        return "file-text"
    if file_type == CLUE_FILE_TYPE and key.startswith("step"):
        return "clue-text"
    if file_type == BOUNTY_FILE_TYPE and key == "script":
        return "bounty-script"
    if file_type == CHALLENGE_FILE_TYPE and key in {"input", "output", "task"}:
        return "challenge-text"
    if file_type == QUEST_GAME_FILE_TYPE and key in {"data", "task"}:
        return "quest-game-text"
    if file_type == NEW_FIREWALL_FILE_TYPE and special_key in {"long_desc", "short_desc"}:
        return "firewall-description"
    return "file-content"


def convert_file_field(
    file_type: int,
    key: str,
    value: str | None,
    path: str,
    blobs: list[dict],
    special_key: str | None = None,
) -> dict | None:
    if value is None:
        return None
    if should_blob_file_content(file_type, key, special_key):
        return blob_field(path, file_blob_kind(file_type, key, special_key), value, blobs)
    return inline_field(value)


def parse_hacker_file(file_node: ET.Element, base_path: str, blobs: list[dict]) -> dict:
    file_type = parse_int(child_text(file_node, "type"), 0)
    content_node = file_node.find("content")
    content: dict[str, dict] = {}
    special_attributes: dict[str, dict[str, dict]] = {}

    for key in file_type_keys(file_type):
        if key in {"specialAttribute1", "specialAttribute2"}:
            key_node = None if content_node is None else content_node.find(key)
            if key_node is None:
                continue
            nested: dict[str, dict] = {}
            for special_key in ["name", "long_desc", "short_desc", "value"]:
                field = convert_file_field(
                    file_type,
                    key,
                    child_text(key_node, special_key),
                    f"{base_path}/content/{key}/{special_key}",
                    blobs,
                    special_key=special_key,
                )
                if field is not None:
                    nested[special_key] = field
            if nested:
                special_attributes[key] = nested
            continue

        key_value = None if content_node is None else child_text(content_node, key)
        field = convert_file_field(file_type, key, key_value, f"{base_path}/content/{key}", blobs)
        if field is not None:
            content[key] = field

    return {
        "type": file_type,
        "name": child_text(file_node, "name", "") or "",
        "location": child_text(file_node, "location", "") or "",
        "description": child_text(file_node, "description", "") or "",
        "price": parse_float(child_text(file_node, "price"), 0.0),
        "quantity": parse_int(child_text(file_node, "quantity"), 0),
        "cpuCost": parse_float(child_text(file_node, "cpu"), 0.0),
        "maker": child_text(file_node, "maker", "") or "",
        "content": content,
        "specialAttributes": special_attributes,
    }


def parse_ports(root: ET.Element, blobs: list[dict]) -> list[dict]:
    ports_node = root.find("ports")
    if ports_node is None:
        return []
    ports = []
    for port_node in ports_node.findall("port"):
        number = parse_int(child_text(port_node, "number"), 0)
        port_type = parse_int(child_text(port_node, "type"), 0)
        firewall_file = None
        firewall_node = port_node.find("firewall")
        if firewall_node is not None:
            file_node = firewall_node.find("file")
            if file_node is not None:
                firewall_file = parse_hacker_file(file_node, f"ports/{number}/firewall", blobs)

        scripts = {}
        code_node = port_node.find("code")
        for key in program_keys_for_port_type(port_type):
            if port_type in {1, 3}:
                script_value = child_text(port_node, key)
            else:
                script_value = child_text(code_node, key) if code_node is not None else None
            if script_value is not None:
                scripts[key] = blob_field(f"ports/{number}/program/{key}", "program-script", script_value, blobs)

        ports.append(
            {
                "number": number,
                "type": port_type,
                "health": parse_float(child_text(port_node, "health"), 0.0),
                "on": parse_bool(child_text(port_node, "onoff")),
                "cpuCost": parse_float(child_text(port_node, "cpu"), 0.0),
                "note": child_text(port_node, "note"),
                "firewall": firewall_file,
                "dummy": parse_bool(child_text(port_node, "dummy")),
                "maliciousTarget": child_text(port_node, "malicioustarget"),
                "scripts": scripts,
            }
        )
    return ports


def parse_watches(root: ET.Element, blobs: list[dict]) -> list[dict]:
    watches_node = root.find("watches")
    if watches_node is None:
        return []
    watches = []
    for index, watch_node in enumerate(watches_node.findall("watch")):
        watches.append(
            {
                "type": parse_int(child_text(watch_node, "type"), 0),
                "searchFireWall": parse_int(child_text(watch_node, "searchfirewall"), 0),
                "cpuCost": parse_float(child_text(watch_node, "cpu"), 0.0),
                "installPort": parse_int(child_text(watch_node, "installport"), 0),
                "note": child_text(watch_node, "note"),
                "on": parse_bool(child_text(watch_node, "on")),
                "observedPorts": [parse_int("".join(node.itertext()), 0) for node in watch_node.findall("observedport")],
                "quantity": parse_float(child_text(watch_node, "quantity"), 0.0),
                "scripts": {
                    "fire": blob_field(
                        f"watches/{index}/program/fire",
                        "watch-script",
                        child_text(watch_node, "fire", "") or "",
                        blobs,
                    )
                },
            }
        )
    return watches


def parse_file_system(root: ET.Element, blobs: list[dict]) -> dict:
    files_node = root.find("files")
    if files_node is None:
        return {"directories": [], "files": []}
    directories = ["".join(node.itertext()) for node in files_node.findall("directory")]
    files = [
        parse_hacker_file(file_node, f"files/{index}", blobs)
        for index, file_node in enumerate(files_node.findall("file"))
    ]
    return {"directories": directories, "files": files}


def parse_equipment(root: ET.Element, blobs: list[dict]) -> list[dict]:
    equipment = []
    for index, equipment_node in enumerate(root.findall("equipment")):
        file_node = equipment_node.find("file")
        equipment.append(
            {
                "file": parse_hacker_file(file_node, f"equipment/{index}", blobs) if file_node is not None else None
            }
        )
    return equipment


def parse_preferences(root: ET.Element) -> dict[str, str]:
    preferences_node = root.find("preferences")
    if preferences_node is None:
        return {}
    result: dict[str, str] = {}
    for pref_node in preferences_node.findall("preference"):
        name = child_text(pref_node, "name")
        if name:
            result[name] = child_text(pref_node, "value", "") or ""
    return result


def parse_stats(root: ET.Element) -> dict:
    stats_node = root.find("stats")
    if stats_node is None:
        stats_node = ET.Element("stats")
    return {
        "attackXp": parse_float(child_text(stats_node, "attackxp"), 0.0),
        "merchantingXp": parse_float(child_text(stats_node, "merchantingxp"), 0.0),
        "firewallXp": parse_float(child_text(stats_node, "firewallxp"), 0.0),
        "watchXp": parse_float(child_text(stats_node, "watchxp"), 0.0),
        "scanningXp": parse_float(child_text(stats_node, "scanningxp"), 0.0),
        "webDesignXp": parse_float(child_text(stats_node, "webdesignxp"), 0.0),
        "redirectingXp": parse_float(child_text(stats_node, "redirectingxp"), 0.0),
        "repairXp": parse_float(child_text(stats_node, "repairxp"), 0.0),
    }


def parse_current_quests(root: ET.Element) -> list[dict]:
    quests = []
    for quest_node in root.findall("currentquest"):
        tasks = []
        for task_node in quest_node.findall("task"):
            tasks.append(
                {
                    "name": child_text(task_node, "name", "") or "",
                    "complete": parse_bool(child_text(task_node, "complete")),
                    "label": child_text(task_node, "label", "") or "",
                }
            )
        quests.append(
            {
                "id": parse_int(child_text(quest_node, "id"), 0),
                "label": child_text(quest_node, "label", "") or "",
                "tasks": tasks,
            }
        )
    return quests


def parse_completed_quests(root: ET.Element) -> list[dict]:
    return [
        {"id": parse_int(child_text(node, "id"), 0), "label": child_text(node, "label", "") or ""}
        for node in root.findall("completedquest")
    ]


def parse_involved_quests(root: ET.Element) -> list[int]:
    return [parse_int(child_text(node, "id"), 0) for node in root.findall("involvedquest")]


def parse_log_entries(root: ET.Element) -> list[dict]:
    entries = []
    for node in root.findall("logentry"):
        entries.append({"ip": node.attrib.get("ip", ""), "message": "".join(node.itertext())})
    return entries


def parse_globals(root: ET.Element) -> list[dict]:
    values = []
    for node in root.findall("global"):
        values.append({"type": node.attrib.get("type", "TYPE"), "value": "".join(node.itertext())})
    while len(values) < 20:
        values.append({"type": "TYPE", "value": None})
    return values[:20]


def parse_float_series(root: ET.Element, section_name: str) -> list[float]:
    section = root.find(section_name)
    values = []
    if section is not None:
        values = [parse_float("".join(node.itertext()), 0.0) for node in section.findall("value")]
    while len(values) < 5:
        values.append(0.0)
    return values[:5]


def parse_website(root: ET.Element, blobs: list[dict]) -> dict:
    website = root.find("website")
    if website is None:
        website = ET.Element("website")
    return {
        "myVotes": parse_int(child_text(website, "myvotes"), 0),
        "storeRevenueTarget": child_text(website, "storerevenue", "") or "",
        "adRevenueTarget": child_text(website, "adrevenue"),
        "title": child_text(website, "title"),
        "body": blob_field("website/body", "website-body", child_text(website, "body"), blobs),
    }


def parse_legacy_extras(root: ET.Element) -> dict[str, str]:
    known = {
        "ip",
        "name",
        "cputype",
        "memorytype",
        "password",
        "hackcount",
        "votecount",
        "playertype",
        "network",
        "dailypaysize",
        "dailyPayReduction",
        "respawnmoney",
        "maximumpettycash",
        "dropTable",
        "currentquest",
        "involvedquest",
        "completedquest",
        "allowedNetwork",
        "logentry",
        "global",
        "hdtype",
        "lastpaid",
        "pettycash",
        "bank",
        "defaultattack",
        "defaultbank",
        "defaultftp",
        "defaulthttp",
        "defaultshipping",
        "stats",
        "commodity",
        "commodityrespawn",
        "ports",
        "watches",
        "files",
        "website",
        "equipment",
        "preferences",
    }
    extras = {}
    for child in list(root):
        if child.tag not in known:
            extras[child.tag] = ET.tostring(child, encoding="unicode")
    return extras


def build_manifest(xml_text: str) -> tuple[dict, list[dict]]:
    root = ET.fromstring(xml_text)
    if root.tag != "save":
        raise ValueError(f"expected <save> root, got <{root.tag}>")

    blobs: list[dict] = []
    manifest = {
        "schemaVersion": 1,
        "ip": child_text(root, "ip", "") or "",
        "name": child_text(root, "name"),
        "cpuType": parse_int(child_text(root, "cputype"), 0),
        "memoryType": parse_int(child_text(root, "memorytype"), 0),
        "password": child_text(root, "password"),
        "hackCount": parse_int(child_text(root, "hackcount"), 0),
        "voteCount": parse_int(child_text(root, "votecount"), 0),
        "playerType": parse_int(child_text(root, "playertype"), 0),
        "network": child_text(root, "network"),
        "dailyPaySize": parse_float(child_text(root, "dailypaysize"), 0.0),
        "dailyPayReduction": parse_float(child_text(root, "dailyPayReduction"), 1.0),
        "respawnMoney": parse_float(child_text(root, "respawnmoney"), 0.0),
        "maximumPettyCash": parse_float(child_text(root, "maximumpettycash"), 0.0),
        "dropTable": parse_int(child_text(root, "dropTable"), 0),
        "currentQuests": parse_current_quests(root),
        "involvedQuests": parse_involved_quests(root),
        "completedQuests": parse_completed_quests(root),
        "allowedNetworks": [child.text or "" for child in root.findall("allowedNetwork")],
        "logEntries": parse_log_entries(root),
        "globals": parse_globals(root),
        "hdType": parse_int(child_text(root, "hdtype"), 0),
        "lastPaid": parse_int(child_text(root, "lastpaid"), 0),
        "pettyCash": parse_float(child_text(root, "pettycash"), 0.0),
        "bank": parse_float(child_text(root, "bank"), 0.0),
        "defaultAttack": parse_int(child_text(root, "defaultattack"), 0),
        "defaultBank": parse_int(child_text(root, "defaultbank"), 0),
        "defaultFtp": parse_int(child_text(root, "defaultftp"), 0),
        "defaultHttp": parse_int(child_text(root, "defaulthttp"), 0),
        "defaultShipping": parse_int(child_text(root, "defaultshipping"), 0),
        "stats": parse_stats(root),
        "commodityAmount": parse_float_series(root, "commodity"),
        "commodityRespawn": parse_float_series(root, "commodityrespawn"),
        "ports": parse_ports(root, blobs),
        "watches": parse_watches(root, blobs),
        "fileSystem": parse_file_system(root, blobs),
        "website": parse_website(root, blobs),
        "equipmentSlots": parse_equipment(root, blobs),
        "preferences": parse_preferences(root),
        "legacyExtras": parse_legacy_extras(root),
    }
    return manifest, blobs


def sql_literal_hex(value: str) -> str:
    encoded = value.encode("utf-8").hex()
    return f"CONVERT(0x{encoded} USING utf8mb4)"


def column_exists(args: argparse.Namespace, table_name: str, column_name: str) -> bool:
    sql = f"""
        SELECT COUNT(*)
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = {json.dumps(args.database)}
          AND TABLE_NAME = {json.dumps(table_name)}
          AND COLUMN_NAME = {json.dumps(column_name)}
    """
    output = run_mysql_query(args, sql).strip()
    return output == "1"


def build_select_sql(args: argparse.Namespace, has_stats_json_column: bool) -> str:
    clauses = ["stats IS NOT NULL"]
    if not args.overwrite_existing_json and has_stats_json_column:
        clauses.append("stats_json IS NULL")
    if args.user_num is not None:
        clauses.append(f"num = {args.user_num}")
    if args.start_num is not None:
        clauses.append(f"num >= {args.start_num}")
    if args.end_num is not None:
        clauses.append(f"num <= {args.end_num}")
    where_clause = " AND ".join(clauses)
    limit_clause = f" LIMIT {args.limit}" if args.limit is not None else ""
    return f"""
        SELECT num, COALESCE(ip, ''), HEX(stats)
        FROM `{args.database}`.`user`
        WHERE {where_clause}
        ORDER BY num ASC{limit_clause}
    """


def build_write_sql(database: str, user_num: int, manifest_json: str, blobs: list[dict], migrated_at: str) -> str:
    statements = [
        "START TRANSACTION;",
        f"UPDATE `{database}`.`user`",
        f"SET `stats_json` = {sql_literal_hex(manifest_json)},",
        "    `stats_json_version` = 1,",
        f"    `stats_json_migrated_at` = '{migrated_at}'",
        f"WHERE `num` = {user_num};",
        f"DELETE FROM `{database}`.`user_stats_text_blob` WHERE `user_num` = {user_num};",
    ]
    if blobs:
        values = []
        for blob in blobs:
            values.append(
                f"({user_num}, {json.dumps(blob['path'])}, {json.dumps(blob['kind'])}, {sql_literal_hex(blob['text_content'])})"
            )
        statements.append(
            f"INSERT INTO `{database}`.`user_stats_text_blob` (`user_num`, `blob_path`, `blob_kind`, `text_content`) VALUES\n"
            + ",\n".join(values)
            + ";"
        )
    statements.append("COMMIT;")
    return "\n".join(statements)


def fetch_rows(args: argparse.Namespace) -> list[tuple[int, str, str]]:
    has_stats_json_column = column_exists(args, "user", "stats_json")
    output = run_mysql_query(args, build_select_sql(args, has_stats_json_column))
    rows = []
    for line in output.splitlines():
        if not line.strip():
            continue
        num_text, ip, stats_hex = line.split("\t", 2)
        rows.append((int(num_text), ip, stats_hex))
    return rows


def main() -> int:
    args = parse_args()
    rows = fetch_rows(args)
    if not rows:
        print("No rows matched the requested backfill criteria.")
        return 0

    migrated = 0
    failures = 0
    for user_num, ip, stats_hex in rows:
        try:
            xml_text = bytes.fromhex(stats_hex).decode("utf-8")
            manifest, blobs = build_manifest(xml_text)
            manifest_json = json.dumps(manifest, ensure_ascii=False, indent=2)
            migrated_at = dt.datetime.now().replace(microsecond=0).isoformat(sep=" ")

            if args.dry_run:
                print(f"DRY RUN user.num={user_num} ip={ip} blobs={len(blobs)} manifest_bytes={len(manifest_json.encode('utf-8'))}")
            else:
                execute_mysql_script(args, build_write_sql(args.database, user_num, manifest_json, blobs, migrated_at))
                print(f"MIGRATED user.num={user_num} ip={ip} blobs={len(blobs)}")
            migrated += 1
        except Exception as exc:
            failures += 1
            print(f"FAILED user.num={user_num} ip={ip}: {exc}", file=sys.stderr)

    print(f"SUMMARY migrated={migrated} failed={failures} dry_run={args.dry_run}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
