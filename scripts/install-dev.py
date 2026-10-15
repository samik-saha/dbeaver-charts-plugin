#!/usr/bin/env python3
"""Install the built plugin in a development DBeaver copy, backing up older versions."""
import argparse
from datetime import datetime
from pathlib import Path
import shutil
import zipfile

from build import ROOT, installation


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dbeaver", type=Path, required=True)
    args = parser.parse_args()
    home = installation(args.dbeaver)
    manifest = (ROOT / "plugins/dev.dbeaver.charts/META-INF/MANIFEST.MF").read_text()
    version = next(line.split(": ", 1)[1] for line in manifest.splitlines()
                   if line.startswith("Bundle-Version: "))
    source = ROOT / "build" / f"dev.dbeaver.charts_{version}.jar"
    with zipfile.ZipFile(source) as archive:
        if archive.testzip() is not None:
            parser.error("Built plugin failed archive integrity check.")
        packaged_manifest = archive.read("META-INF/MANIFEST.MF").decode().replace("\r\n ", "")
        if f"Bundle-Version: {version}\r\n" not in packaged_manifest:
            parser.error("Built plugin version does not match the source manifest. Rebuild first.")
    destination = home / "dropins/dbeaver-charts/eclipse/plugins"
    destination.mkdir(parents=True, exist_ok=True)
    previous = list(destination.glob("dev.dbeaver.charts_*.jar"))
    backup = ROOT / "build/install-backups" / datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    if previous:
        backup.mkdir(parents=True)
        for jar in previous:
            shutil.copy2(jar, backup / jar.name)
    # Fully stage the new archive before replacing/removing any installed version.
    staged = destination / (source.name + ".tmp")
    shutil.copy2(source, staged)
    staged.replace(destination / source.name)
    for jar in previous:
        if jar.name != source.name:
            jar.unlink()
    print(f"Installed {destination / source.name}")
    if previous:
        print(f"Previous plugin backed up to {backup}")
    print("Save work, quit this DBeaver copy, then restart with -clean.")


if __name__ == "__main__":
    main()
