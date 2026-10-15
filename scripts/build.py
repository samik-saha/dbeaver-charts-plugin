#!/usr/bin/env python3
"""Build against a local DBeaver installation; no downloaded dependencies."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "plugins" / "dev.dbeaver.charts"


def jdk_tool(name):
    executable = name + (".exe" if os.name == "nt" else "")
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    resolved = shutil.which(executable)
    if not resolved:
        raise ValueError(f"{name} not found. Install JDK 21+ and set JAVA_HOME or PATH.")
    return resolved


def installation(path):
    candidates = [path / "Contents" / "Eclipse", path]
    for candidate in candidates:
        if (candidate / "plugins").is_dir():
            return candidate
    raise ValueError(f"No plugins directory under {path}. Pass the DBeaver app or installation directory.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dbeaver", type=Path, default=os.environ.get("DBEAVER_HOME"),
                        help="DBeaver .app on macOS, or the installation directory on Windows/Linux")
    args = parser.parse_args()
    if args.dbeaver is None:
        default = Path("/Applications/DBeaver.app")
        if not default.is_dir():
            parser.error("Set --dbeaver or DBEAVER_HOME to a DBeaver installation.")
        args.dbeaver = default
    home = installation(args.dbeaver)
    if not list((home / "plugins").glob("org.jkiss.dbeaver.ui.editors.data_*.jar")):
        raise ValueError("This installation does not contain DBeaver's result-set editor bundle.")
    javac = jdk_tool("javac")
    java = jdk_tool("java")
    jar = jdk_tool("jar")
    output = ROOT / "build"
    output.mkdir(exist_ok=True)
    manifest = (PLUGIN / "META-INF" / "MANIFEST.MF").read_text()
    version = next(line.split(": ", 1)[1] for line in manifest.splitlines()
                   if line.startswith("Bundle-Version: "))
    name = f"dev.dbeaver.charts_{version}.jar"
    with tempfile.TemporaryDirectory(prefix="compile-", dir=output) as temp:
        staging = Path(temp)
        classes = staging / "classes"
        classes.mkdir()
        # javac @argfile handles large source lists and Windows command-line limits.
        sources = sorted((PLUGIN / "src").rglob("*.java"))
        source_list = staging / "sources.txt"
        source_list.write_text("\n".join('"' + path.as_posix() + '"' for path in sources))
        subprocess.run([javac, "--release", "21", "-encoding", "UTF-8",
                        "-classpath", str(home / "plugins" / "*"),
                        "-d", str(classes), "@" + str(source_list)], check=True)
        tests = staging / "tests"
        tests.mkdir()
        subprocess.run([javac, "--release", "21", "-encoding", "UTF-8",
                        "-classpath", str(classes), "-d", str(tests),
                        *map(str, sorted((ROOT / "tests").rglob("*.java")))], check=True)
        for test in ("ChartValuesTest", "ChartSeriesTest", "ChartGroupsTest", "ChartTypesTest", "ChartCombinedTest"):
            subprocess.run([java, "-classpath", os.pathsep.join([str(classes), str(tests)]),
                            "dev.dbeaver.charts." + test], check=True)
        subprocess.run([jar, "--create", "--file", str(output / name),
                        "--manifest", str(PLUGIN / "META-INF/MANIFEST.MF"),
                        "-C", str(classes), ".", "-C", str(PLUGIN), "plugin.xml",
                        "-C", str(PLUGIN), "icons"], check=True)
    archive = output / f"dbeaver-charts-{version}-dropins.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        bundle.write(output / name, f"dropins/dbeaver-charts/eclipse/plugins/{name}")
    print(f"Built against: {home}\nPlugin: {output / name}\nDrop-in archive: {archive}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f"Build failed: {error}", file=sys.stderr)
        sys.exit(1)
