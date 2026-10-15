#!/usr/bin/env python3
"""Enable Eclipse's drop-in loader in a development DBeaver installation."""
import argparse
from pathlib import Path
import shutil

from build import installation


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dbeaver", type=Path, required=True)
    args = parser.parse_args()
    home = installation(args.dbeaver)
    loaders = sorted((home / "plugins").glob("org.eclipse.equinox.p2.reconciler.dropins_*.jar"))
    if len(loaders) != 1:
        parser.error("Expected exactly one installed drop-in reconciler bundle.")
    config = home / "configuration/config.ini"
    text = config.read_text()
    lines = text.splitlines(keepends=True)
    for index, line in enumerate(lines):
        if not line.startswith("osgi.bundles="):
            continue
        if "org.eclipse.equinox.p2.reconciler.dropins" in line:
            parser.error("The loader is already explicitly configured; inspect config.ini before changing it.")
        if line.rstrip().endswith("\\"):
            parser.error("Multiline osgi.bundles property requires manual editing.")
        backup = config.with_name("config.ini.before-charts")
        if backup.exists():
            parser.error(f"Backup already exists: {backup}. Inspect it before making further changes.")
        lines[index] = (line.rstrip("\r\n") + ",reference\\:file\\:"
                        + loaders[0].name + "@4\\:start\n")
        shutil.copy2(config, backup)
        with config.open("w", encoding="utf-8", newline="") as stream:
            stream.write("".join(lines))
        print(f"Enabled drop-in scanning in {config}")
        print(f"Original configuration backed up to {backup}")
        print("Quit and restart this DBeaver copy with -clean.")
        return
    parser.error("No osgi.bundles property found in config.ini.")


if __name__ == "__main__":
    main()
