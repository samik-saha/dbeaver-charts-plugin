#!/usr/bin/env python3
"""Compile and capture native macOS previews with production widgets and synthetic data."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

from build import ROOT, installation, jdk_tool


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dbeaver", type=Path, required=True)
    args = parser.parse_args()
    if sys.platform != "darwin":
        parser.error("Automated screenshot capture currently requires macOS.")
    home = installation(args.dbeaver)
    version = next(line.split(": ", 1)[1] for line in
                   (ROOT / "plugins/dev.dbeaver.charts/META-INF/MANIFEST.MF").read_text().splitlines()
                   if line.startswith("Bundle-Version: "))
    plugin = ROOT / "build" / f"dev.dbeaver.charts_{version}.jar"
    classes = ROOT / "build/preview-classes"
    classes.mkdir(exist_ok=True)
    classpath = os.pathsep.join([str(plugin), str(home / "plugins/*")])
    subprocess.run([jdk_tool("javac"), "--release", "21", "-encoding", "UTF-8", "-classpath", classpath,
                    "-d", str(classes), str(ROOT / "preview/dev/dbeaver/charts/ChartPreview.java")], check=True)
    subprocess.run([jdk_tool("java"), "-XstartOnFirstThread", "-classpath",
                    os.pathsep.join([str(classes), classpath]), "dev.dbeaver.charts.ChartPreview",
                    str(ROOT / "build/visual-qa")], check=True)


if __name__ == "__main__":
    main()
