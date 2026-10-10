#!/usr/bin/env python3
"""Refuse an overwrite unless Modrinth has the exact release artifact."""
import hashlib
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen

project = os.environ["PROJECT_ID"]
version = os.environ["VERSION"]
artifact = Path(f"build/libs/dynamicskateboards-{version}.jar").read_bytes()
request = Request(f"https://api.modrinth.com/v2/project/{project}/version",
                  headers={"Authorization": os.environ["MODRINTH_TOKEN"],
                           "User-Agent": "brooswit-minecraft/dynamic-skateboards-release"})
with urlopen(request, timeout=30) as response:
    matches = [item for item in json.load(response) if item["version_number"] == version]
if matches:
    item = matches[0]
    valid = (len(matches) == 1 and item["loaders"] == ["neoforge"]
             and item["game_versions"] == ["1.21.1"] and item["version_type"] == "alpha"
             and len(item["files"]) == 1
             and item["files"][0]["hashes"]["sha512"] == hashlib.sha512(artifact).hexdigest())
    if not valid:
        raise SystemExit("Existing Modrinth version differs; bump the version. Never overwrite.")
print(f"exists={'true' if matches else 'false'}")
