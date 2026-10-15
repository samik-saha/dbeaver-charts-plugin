#!/usr/bin/env python3
"""Build a p2 update site (build/update-site) users can add via Help > Install New Software.

Uses the p2 publisher bundled inside DBeaver, so no Maven/Tycho is needed. Run build.py first.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

from build import ROOT, PLUGIN, installation, jdk_tool

FEATURE_ID = "dev.dbeaver.charts.feature"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dbeaver", type=Path, default=os.environ.get("DBEAVER_HOME"))
    args = parser.parse_args()
    if args.dbeaver is None:
        args.dbeaver = Path("/Applications/DBeaver.app")
    home = installation(args.dbeaver)
    manifest = (PLUGIN / "META-INF/MANIFEST.MF").read_text()
    version = next(
        (l.split(": ", 1)[1].strip() for l in manifest.splitlines() if l.startswith("Bundle-Version: ")),
        None,
    )
    if version is None:
        raise ValueError("META-INF/MANIFEST.MF is missing Bundle-Version.")
    plugin_jar = ROOT / "build" / f"dev.dbeaver.charts_{version}.jar"
    if not plugin_jar.is_file():
        parser.error("Plugin jar missing. Run scripts/build.py first.")

    work = ROOT / "build/p2-work"
    site = ROOT / "build/update-site"
    for path in (work, site):
        shutil.rmtree(path, ignore_errors=True)
    (work / "source/features").mkdir(parents=True)
    (work / "source/plugins").mkdir(parents=True)
    shutil.copy2(plugin_jar, work / "source/plugins")

    feature_dir = work / "source/features" / f"{FEATURE_ID}_{version}"
    feature_dir.mkdir()
    (feature_dir / "feature.xml").write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<feature id="{FEATURE_ID}" label="DBeaver Charts" version="{version}"
         provider-name="DBeaver Charts contributors">
   <description>Adds a Charts panel (line, column, bar) beside DBeaver query results.</description>
   <copyright>DBeaver Charts contributors</copyright>
   <license url="https://www.apache.org/licenses/LICENSE-2.0">Apache License 2.0</license>
   <plugin id="dev.dbeaver.charts" download-size="0" install-size="0" version="{version}" unpack="false"/>
</feature>
''')
    # The publisher expects a feature directory; it is also fine to publish it as a jar.
    category = work / "category.xml"
    category.write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<site>
   <feature id="{FEATURE_ID}" version="{version}">
      <category name="dbeaver.charts"/>
   </feature>
   <category-def name="dbeaver.charts" label="DBeaver Charts"/>
</site>
''')

    plugins = home / "plugins"
    launcher = next(plugins.glob("org.eclipse.equinox.launcher_*.jar"), None)
    if launcher is None:
        launcher = next(plugins.glob("org.jkiss.dbeaver.launcher_*.jar"), None)
    if launcher is None:
        raise ValueError(f"No Equinox or DBeaver launcher jar in {plugins}.")
    java = home / "jre/Contents/Home/bin/java"
    if not java.exists():
        java = home / "jre/bin/java"
    java = str(java) if java.exists() else jdk_tool("java")
    url = (site.resolve()).as_uri()

    def run(application, *extra):
        cmd = [java, "-jar", str(launcher), "-nosplash", "-application", application,
               "-configuration", str(work / "cfg"), "-data", str(work / "data"), *extra]
        subprocess.run(cmd, check=True)

    source = (work / "source").resolve().as_uri()
    run("org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher",
        "-metadataRepository", url, "-artifactRepository", url,
        "-source", str(work / "source"), "-compress", "-publishArtifacts")
    run("org.eclipse.equinox.p2.publisher.CategoryPublisher",
        "-metadataRepository", url, "-categoryDefinition", category.resolve().as_uri(),
        "-compress")
    archive = ROOT / "build" / f"dbeaver-charts-{version}-update-site.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for file in sorted(site.rglob("*")):
            if file.is_file():
                bundle.write(file, file.relative_to(site).as_posix())
    print(f"Update site: {site}\nArchive: {archive}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f"Failed: {error}", file=sys.stderr)
        sys.exit(1)

