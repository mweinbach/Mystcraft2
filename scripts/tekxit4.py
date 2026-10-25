#!/usr/bin/env python3
"""Fetch the official Tekxit 4 server, install the Fabric ports into a disposable copy and probe it.

  fetch    download and verify the server pack and the Fabric GameTest API into the cache
  build    build every Fabric port, ICBM's Tekxit test jar and the boot probe
  prepare  recreate the disposable server with the built jars installed
  run      start it, run the probe's checks and GameTests, stop it and report
  release  build the drop-in zip of every Fabric port for a player's Tekxit 4 mods/ folder

The pack is downloaded from tekxit.lol and never committed. Minecraft's EULA is accepted only when
the operator sets MINECRAFT_EULA=true; nothing here accepts it on their behalf.
"""
import argparse
import hashlib
import io
import itertools
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get("TEKXIT4_CACHE") or Path.home() / ".cache/mystcraft2/tekxit4")
SERVER_DIR = ROOT / ".local/tekxit4/server-ports"
MARKER = ".tekxit4-disposable"
PACK = {
    "url": "https://tekxit.lol/downloads/tekxit4/20.0.0Tekxit4Server.zip",
    "sha256": "23434145235ab5b36f1d5597f01241dcfc2f7f65d60ede1e1357e2592c24dcf6",
    "root": "20.0.0Tekxit4Server/",
}
# The pack's Fabric API umbrella omits the GameTest module that provides /test on a dedicated server.
GAMETEST_API = {
    "url": "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-gametest-api-v1/1.1.8+4d0d570390/"
           "fabric-gametest-api-v1-1.1.8+4d0d570390.jar",
    "sha256": "649f3ab85eaed0929d318ad9a51078035c5a01aacd68da2b45d22b0c353f08ba",
}
PROBE_LIBS = ROOT / "tools/tekxit4-probe/build/libs"
ICBM_LIBS = ROOT / "mods/icbm_port/build/libs"
NOT_PRODUCTION = ("-sources", "-named", "-javadoc", "-tests", "smoke")
RELEASE_DIR = ROOT / "build/tekxit4-release"
RELEASE_PACK = "Tekxit 4 20.0.4"
# What Tekxit 4 20.0.4 itself provides outside its mods/ folder.
RELEASE_ENVIRONMENT = {"minecraft": "1.19.2", "java": "17", "fabricloader": "0.18.4"}
# MetalRender declares `breaks: sodium` and Tekxit ships Sodium, so it is a separate, optional download.
METALRENDER_LIBS = ROOT / "mods/metalrender/build/libs"
RELEASE_FIXED_TIME = (2020, 1, 1, 0, 0, 0)
SERVER_PROPERTIES = {
    "online-mode": "false",
    "server-ip": "127.0.0.1",
    "server-port": "25567",
    "level-seed": "12345",
    "view-distance": "3",
    "simulation-distance": "3",
    "motd": "Disposable Tekxit 4 test server",
}


def dual_ports():
    return sorted(path.parent.parent.name for path in (ROOT / "mods").glob("*/fabric/build.gradle"))


def port_libs(port):
    return ICBM_LIBS if port == "icbm_port" else ROOT / "mods" / port / "fabric/build/libs"


def all_ports():
    return dual_ports() + ["icbm_port"]


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def download(source, target):
    if target.is_file() and sha256(target) == source["sha256"]:
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name(target.name + ".part")
    print(f"Downloading {source['url']}")
    subprocess.run(["curl", "--fail", "--location", "--retry", "3", "--silent", "--show-error",
                    source["url"], "-o", str(partial)], check=True)
    if sha256(partial) != source["sha256"]:
        partial.unlink()
        raise SystemExit(f"SHA-256 mismatch for {source['url']}")
    partial.replace(target)
    return target


def pristine():
    return CACHE / "server-20.0.0"


def unpack(archive, staging):
    with zipfile.ZipFile(archive) as pack:
        for entry in pack.infolist():
            name = entry.filename
            if not name.startswith(PACK["root"]) or ".." in Path(name).parts or Path(name).is_absolute():
                raise SystemExit(f"Unexpected archive entry: {name}")
            relative = name[len(PACK["root"]):]
            if not relative or entry.is_dir():
                continue
            target = staging / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            with pack.open(entry) as source, target.open("wb") as sink:
                shutil.copyfileobj(source, sink)


def command_fetch(args):
    base = pristine()
    if (base / ".source-sha256").is_file() and (base / ".source-sha256").read_text().strip() == PACK["sha256"]:
        print(f"Tekxit 4 server 20.0.0 already unpacked in {base}")
    else:
        archive = download(PACK, CACHE / "20.0.0Tekxit4Server.zip")
        staging = Path(tempfile.mkdtemp(dir=CACHE, prefix="unpack-"))
        try:
            unpack(archive, staging)
        except BaseException:
            shutil.rmtree(staging, ignore_errors=True)
            raise
        # The archive ships eula=true. Reset it; prepare writes the operator's own choice.
        (staging / "eula.txt").write_text("eula=false\n")
        (staging / ".source-sha256").write_text(PACK["sha256"] + "\n")
        if base.exists():
            shutil.rmtree(base)
        staging.rename(base)
        if not args.keep_archive:
            archive.unlink()
        print(f"Unpacked Tekxit 4 server 20.0.0 into {base}")
    download(GAMETEST_API, CACHE / Path(GAMETEST_API["url"]).name)
    print("Fabric GameTest API ready.")


def gradle(*arguments):
    print("+ ./scripts/gradle.sh " + " ".join(arguments), flush=True)
    subprocess.run([str(ROOT / "scripts/gradle.sh"), *arguments], cwd=ROOT, check=True)


def selected(args):
    ports = args.ports.split(",") if args.ports else all_ports()
    unknown = sorted(set(ports) - set(all_ports()))
    if unknown:
        raise SystemExit(f"Unknown Fabric ports: {', '.join(unknown)} (choose from {', '.join(all_ports())})")
    return ports


def command_build(args):
    for port in selected(args):
        if port == "icbm_port":
            gradle(":icbm_port:assemble", ":icbm_port:remapTekxitTestJar")
        else:
            gradle("-p", f"mods/{port}/fabric", "assemble")
    gradle("-p", "tools/tekxit4-probe", "assemble")


def production_jars(libs):
    jars = [jar for jar in libs.glob("*.jar") if not any(tag in jar.stem for tag in NOT_PRODUCTION)]
    names = {jar.name for jar in jars}
    # A "-dev" jar is a development twin only when the plain jar sits beside it; versions such as 0.4.0-dev
    # (Mystcraft, Advanced Genetics) end in "-dev" themselves and are the production jar.
    return sorted(jar for jar in jars if not (jar.stem.endswith("-dev") and jar.stem[:-4] + ".jar" in names))


def eula_accepted():
    return os.environ.get("MINECRAFT_EULA", "").strip().lower() == "true"


def write_properties(path, changes):
    lines, seen = [], set()
    for line in path.read_text().splitlines():
        key = line.split("=", 1)[0]
        if not line.startswith("#") and key in changes:
            line = f"{key}={changes[key]}"
            seen.add(key)
        lines.append(line)
    lines += [f"{key}={value}" for key, value in changes.items() if key not in seen]
    path.write_text("\n".join(lines) + "\n")


def command_prepare(args):
    base = pristine()
    if not (base / ".source-sha256").is_file():
        raise SystemExit(f"No unpacked server in {base}. Run: python3 scripts/tekxit4.py fetch")
    target = Path(args.dir).resolve()
    if target.exists():
        if not (target / MARKER).is_file():
            raise SystemExit(f"{target} exists and was not made by this script; remove it yourself.")
        shutil.rmtree(target)
    shutil.copytree(base, target)
    (target / MARKER).write_text("Recreated by scripts/tekxit4.py prepare. Do not keep worlds here.\n")
    write_properties(target / "server.properties", SERVER_PROPERTIES)
    if eula_accepted():
        (target / "eula.txt").write_text("# Accepted by the operator through MINECRAFT_EULA=true "
                                         "(https://aka.ms/MinecraftEULA).\neula=true\n")
    mods = target / "mods"
    installed = []
    for port in selected(args):
        jars = production_jars(port_libs(port))
        if not jars:
            raise SystemExit(f"No built jars for {port} in {port_libs(port)}. Run: python3 scripts/tekxit4.py build")
        installed += jars
    if not args.no_probe:
        probe = production_jars(PROBE_LIBS)
        if not probe:
            raise SystemExit("The boot probe is not built. Run: python3 scripts/tekxit4.py build")
        installed += probe + [CACHE / Path(GAMETEST_API["url"]).name]
        icbm_tests = sorted(ICBM_LIBS.glob("icbm-fabric-port-*-tekxit-tests.jar"))
        if "icbm_port" in selected(args) and icbm_tests:
            installed.append(icbm_tests[-1])
    for jar in installed:
        if not jar.is_file():
            raise SystemExit(f"Missing {jar}. Run: python3 scripts/tekxit4.py fetch")
        shutil.copy2(jar, mods / jar.name)
    if not args.no_item_alchemy:
        shutil.copy2(target / "TKXAddons/itemalchemy.jar", mods / "itemalchemy.jar")
        ports = [p for p in selected(args) if (ROOT / "mods" / p / "emc/values.json").is_file()]
        subprocess.run([sys.executable, str(ROOT / "scripts/emc.py"), "merge-itemalchemy",
                        str(target / "config/itemalchemy/emc_config.json"), *ports], cwd=ROOT, check=True)
        expectations = target / "tekxit-probe/emc"
        expectations.mkdir(parents=True, exist_ok=True)
        for port in ports:
            shutil.copy2(ROOT / "mods" / port / "emc/values.json", expectations / f"{port}.json")
    manifest = {jar.name: sha256(mods / jar.name) for jar in installed}
    (target / "installed-jars.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Prepared {target} with {len(installed)} added jar(s):")
    for name in manifest:
        print(f"  {name}")
    if not eula_accepted():
        print("eula.txt says false: set MINECRAFT_EULA=true in the environment to accept Minecraft's EULA "
              "before `run`.")


def command_run(args):
    target = Path(args.dir).resolve()
    if not (target / MARKER).is_file():
        raise SystemExit(f"{target} is not a prepared server. Run: python3 scripts/tekxit4.py prepare")
    if "eula=true" not in (target / "eula.txt").read_text():
        raise SystemExit("Minecraft's EULA is not accepted for this server. Only the operator can accept it: "
                         "read https://aka.ms/MinecraftEULA, set MINECRAFT_EULA=true, then run prepare again.")
    java_home = os.environ.get("JAVA_HOME") or str(ROOT / ".local/jdk-17")
    java = Path(java_home) / "bin/java"
    if not java.is_file():
        raise SystemExit("No Java 17. Run ./scripts/setup.sh or set JAVA_HOME.")
    out = target / "tekxit-probe"
    for stale in ("gametests.json", "server-stopped.txt"):
        (out / stale).unlink(missing_ok=True)
    for stale in out.glob("server-report-*.json"):
        stale.unlink()
    tests = args.tests
    if tests is None:
        tests = "TekxitProbeGameTests"
        if any(target.glob("mods/icbm-fabric-port-*-tekxit-tests.jar")):
            tests += ",TekxitCompatibilityGameTests"
    command = [str(java), f"-Xms{args.heap}", f"-Xmx{args.heap}", f"-XX:ActiveProcessorCount={os.cpu_count() or 4}",
               "-Dfabric-api.gametest.command=true", f"-Dtekxitprobe.runTests={tests}",
               "-Dtekxitprobe.stopAfterTests=true", "-jar", "Fab_Serv_LaunchWithBatOrSh.jar", "nogui"]
    console = target / "console.log"
    print(f"Starting the Tekxit 4 server in {target}; console in {console}", flush=True)
    started = time.monotonic()
    with console.open("w") as log:
        server = subprocess.Popen(command, cwd=target, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                                  text=True)
        stopped_at = None
        while server.poll() is None:
            time.sleep(5)
            elapsed = time.monotonic() - started
            if stopped_at is None and (out / "server-stopped.txt").is_file():
                stopped_at = elapsed
            if stopped_at is not None and elapsed - stopped_at > 60:
                # Distant Horizons can keep the JVM alive after every dimension saved.
                print("Server stopped but the JVM lingered for 60 s; terminating it (known pack behaviour).")
                server.terminate()
            elif stopped_at is None and elapsed > args.timeout:
                print(f"No result after {args.timeout} s; sending stop.")
                server.stdin.write("stop\n")
                server.stdin.flush()
                stopped_at = elapsed
        server.wait(timeout=60)
    return report(target, server.returncode)


def report(target, returncode):
    out = target / "tekxit-probe"
    ok = True
    reports = sorted(out.glob("server-report-*.json"))
    if not reports:
        print("FAIL: the probe wrote no server report.")
        ok = False
    for path in reports:
        data = json.loads(path.read_text())
        failures, warnings = data.get("failures", []), data.get("warnings", [])
        print(f"{path.name}: {len(failures)} failure(s), {len(warnings)} warning(s)")
        for failure in failures:
            print(f"  FAIL {failure}")
        ok &= not failures
    tests = out / "gametests.json"
    if tests.is_file():
        data = json.loads(tests.read_text())
        print(f"GameTests ({data['end']}): {data['passed']} passed, {data['failed']} failed of {data['expected']}")
        for result in data["results"]:
            if not result["passed"]:
                print(f"  FAIL {result['test']}: {result.get('error')}")
        ok &= data["expected"] > 0 and data["failed"] == 0 and data["passed"] == data["expected"]
    else:
        print("FAIL: no gametests.json; the GameTests did not finish.")
        ok = False
    print(f"JVM exit status {returncode}. Logs: {target / 'logs/latest.log'}, {target / 'console.log'}")
    print("PASSED" if ok else "FAILED")
    return 0 if ok else 1


def jar_mods(data):
    """The fabric.mod.json of a jar and of every jar nested in it, recursively."""
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
        meta = json.loads(archive.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
    except (zipfile.BadZipFile, KeyError):
        return []
    found = [meta]
    for nested in meta.get("jars", []):
        try:
            found += jar_mods(archive.read(nested["file"]))
        except KeyError:
            pass
    return found


def offer(offered, metas):
    for meta in metas:
        for name in [meta["id"], *meta.get("provides", [])]:
            offered.setdefault(name, set()).add(str(meta.get("version", "0")))
    return offered


def pack_mods(path):
    """Mod id -> versions in a pack's mods folder (a directory, or a zip such as 20.0.4Tekxit4.zip)."""
    path = Path(path)
    offered = {}
    if path.is_dir():
        mods = path / "mods" if (path / "mods").is_dir() else path
        for jar in sorted(mods.glob("*.jar")):
            offer(offered, jar_mods(jar.read_bytes()))
    else:
        with zipfile.ZipFile(path) as pack:
            for name in pack.namelist():
                if name.endswith(".jar") and Path(name).parent.name == "mods":
                    offer(offered, jar_mods(pack.read(name)))
    if not offered:
        raise SystemExit(f"No mods found in {path}")
    return offered


def version_key(version):
    """Orders versions as Fabric Loader's semantic versions do: numeric parts, then pre-releases first."""
    core, _, pre = str(version).split("+", 1)[0].partition("-")
    parts = [int("".join(itertools.takewhile(str.isdigit, part)) or 0) for part in core.split(".")]
    while len(parts) > 1 and parts[-1] == 0:
        parts.pop()
    return tuple(parts), (1,) if not pre else (0, pre)


def satisfies(version, constraint):
    if isinstance(constraint, list):
        return any(satisfies(version, each) for each in constraint)
    have = version_key(version)
    for term in constraint.split():
        if term == "*":
            continue
        operator = next((op for op in (">=", "<=", ">", "<", "=", "^", "~") if term.startswith(op)), "")
        want = version_key(term[len(operator):])
        if operator in ("^", "~"):
            width = 1 if operator == "^" else 2
            ok = have >= want and (have[0] + (0, 0))[:width] == (want[0] + (0, 0))[:width]
        else:
            ok = {">=": have >= want, "<=": have <= want, ">": have > want, "<": have < want}.get(operator, have == want)
        if not ok:
            return False
    return True


def check_against_pack(jars, offered):
    """Unmet hard dependencies, `breaks` hits, and the dependencies the pack supplies, for jars added to a pack."""
    ours = {jar: jar_mods(jar.read_bytes()) for jar in jars}
    available = {name: set(versions) for name, versions in offered.items()}
    for metas in ours.values():
        offer(available, metas)
    available.update({name: {version} for name, version in RELEASE_ENVIRONMENT.items()})
    own = set(offer({}, [meta for metas in ours.values() for meta in metas])) | set(RELEASE_ENVIRONMENT)
    unmet, conflicts, from_pack = [], [], {}
    for jar, metas in ours.items():
        for depth, meta in enumerate(metas):
            for dep, constraint in meta.get("depends", {}).items():
                versions = available.get(dep, set())
                chosen = max(versions, key=version_key) if versions else None
                if chosen is None or not satisfies(chosen, constraint):
                    unmet.append(f"{jar.name}: {meta['id']} needs {dep} {constraint} "
                                 f"(found: {', '.join(sorted(versions)) or 'none'})")
                elif dep not in own and depth == 0:
                    from_pack.setdefault(dep, (chosen, set()))[1].add(str(constraint))
            for dep, constraint in meta.get("breaks", {}).items():
                hit = [version for version in offered.get(dep, ()) if satisfies(version, constraint)]
                if hit:
                    conflicts.append(f"{jar.name}: {meta['id']} breaks {dep} {constraint} (pack has {hit[0]})")
    return unmet, conflicts, from_pack


def git_commit():
    head = subprocess.run(["git", "rev-parse", "--short=8", "HEAD"], cwd=ROOT, capture_output=True, text=True)
    dirty = subprocess.run(["git", "status", "--porcelain", "--untracked-files=no"], cwd=ROOT,
                           capture_output=True, text=True)
    return (head.stdout.strip() or "unknown") + ("-dirty" if dirty.stdout.strip() else "")


def write_release_zip(path, jars, readme):
    sums = "".join(f"{sha256(jar)}  {jar.name}\n" for jar in jars)
    entries = [(jar.name, jar.read_bytes(), zipfile.ZIP_STORED) for jar in jars]
    entries += [("README.txt", readme.encode(), zipfile.ZIP_DEFLATED),
                ("SHA256SUMS", sums.encode(), zipfile.ZIP_DEFLATED)]
    with zipfile.ZipFile(path, "w") as archive:
        for name, data, method in entries:
            info = zipfile.ZipInfo(name, RELEASE_FIXED_TIME)
            info.compress_type = method
            info.external_attr = 0o644 << 16
            archive.writestr(info, data)


def release_readme(label, jars, from_pack, metalrender_zip):
    rows = []
    nested = {}
    for jar in jars:
        metas = jar_mods(jar.read_bytes())
        top = metas[0]
        rows.append(f"  {jar.name}\n      {top.get('name', top['id'])} ({top['id']} {top['version']}), "
                    f"licence {top.get('license', 'see repository')}")
        for meta in metas[1:]:
            nested.setdefault(meta["id"], meta.get("version"))
    libraries = [f"  {dep} {' or '.join(sorted(needs))}: the pack has {chosen}"
                 for dep, (chosen, needs) in sorted(from_pack.items())]
    inside = ", ".join(f"{mod_id} {version}" for mod_id, version in sorted(nested.items()))
    return f"""Mystcraft2 Fabric ports for {RELEASE_PACK} ({label})
https://github.com/mweinbach/Mystcraft2

Requires {RELEASE_PACK}: Minecraft 1.19.2, Fabric Loader 0.18.4, Fabric API 0.77.0+1.19.2, Java 17.
These are alpha and development builds. Back up your worlds and try them in a new world first.

INSTALL
1. Close the game (and the server).
2. Delete older copies of any of these mods from the pack's mods/ folder.
3. Copy every .jar in this zip into mods/. Install the same jars on the server and on every client.
   README.txt and SHA256SUMS do not need to be copied.

JARS ({len(jars)})
{chr(10).join(rows)}

EXTRA DEPENDENCIES
None. {RELEASE_PACK} already ships every library these jars need and do not bundle themselves:
{chr(10).join(libraries)}
Bundled inside the jars (jar-in-jar, do not install them separately): {inside}.

NOT IN THIS ZIP
- MetalRender (Metal renderer for macOS) is the separate optional download {metalrender_zip}. It declares
  "breaks: sodium", and Tekxit ships Sodium, so Fabric Loader refuses to start with it until Sodium and the
  three mods that need Sodium are removed. Read the README.txt in that zip first.
- Fluid Physics is not a separate jar: Mystcraft, Extreme Reactors and Mekanism carry it inside.

ITEM ALCHEMY (optional Tekxit add-on)
Tekxit's optional TKXAddons/itemalchemy.jar reads the pack's config/itemalchemy/emc_config.json and ignores
the default EMC values the ports ship. To give port items Item Alchemy EMC, run this from a checkout of the
repository, then restart or run /itemalchemy reloademc:
  python3 scripts/emc.py merge-itemalchemy <pack>/config/itemalchemy/emc_config.json
ProjectE (in this zip) works out its own EMC from the loaded recipes, with or without Item Alchemy. The two keep
separate EMC values and separate items.

KNOWN ISSUES
- Several default key bindings collide with pack keys (ProjectE G/V/C/R, Advanced Genetics G/H, JurassiCraft).
  Rebind them in Options > Controls.
- Test results for this build: docs/tekxit4-testing.md in the repository.

VERIFY
  shasum -a 256 -c SHA256SUMS
"""


METALRENDER_README = """MetalRender for {pack} ({label}) - OPTIONAL
https://github.com/mweinbach/Mystcraft2/tree/main/mods/metalrender

A Metal 4 terrain renderer for Minecraft 1.19.2 on Apple silicon Macs. It is not part of the main zip
because it declares "breaks: sodium", and {pack} ships Sodium. With Sodium present, Fabric Loader refuses to
start: "Mod 'MetalRender' (metalrender) is incompatible with any version of mod 'Sodium' (sodium)".

To use it, on the client only (macOS):
1. Move these four jars out of the pack's mods/ folder: Sodium 0.4.4, Indium 1.0.9, Sodium Extra 0.4.16 and
   Reese's Sodium Options 1.6.4.
2. Copy {jar} into mods/.
To go back to the stock renderer, remove MetalRender and put the four jars back.

Known: with MetalRender, Distant Horizons logs a few "Failed to get block color" warnings for water.

VERIFY
  shasum -a 256 -c SHA256SUMS
"""


def command_release(args):
    if not args.skip_build:
        for port in all_ports():
            if port == "icbm_port":
                gradle(":icbm_port:assemble")
            else:
                gradle("-p", f"mods/{port}/fabric", "assemble")
        gradle("-p", "mods/metalrender", "assemble")
    jars = []
    for port in all_ports():
        found = production_jars(port_libs(port))
        if not found:
            raise SystemExit(f"No built jars for {port} in {port_libs(port)}. Run without --skip-build.")
        jars += found
    metalrender = production_jars(METALRENDER_LIBS)
    if len(metalrender) != 1:
        raise SystemExit(f"Expected one MetalRender jar in {METALRENDER_LIBS}, found {len(metalrender)}.")
    packs = args.pack or ([str(pristine())] if (pristine() / "mods").is_dir() else [])
    if not packs:
        raise SystemExit("No pack to check the dependencies against. Run: python3 scripts/tekxit4.py fetch, "
                         "or pass --pack <20.0.4Tekxit4.zip or a mods folder>.")
    from_pack = None
    for pack in packs:
        offered = pack_mods(pack)
        unmet, conflicts, supplied = check_against_pack(jars, offered)
        print(f"Dependencies against {pack}: {len(unmet)} unmet, {len(conflicts)} conflicts")
        for problem in unmet + conflicts:
            print(f"  {problem}")
        if unmet or conflicts:
            raise SystemExit("The release jars do not fit the pack; fix the ports or bundle the dependency.")
        _, metal_conflicts, _ = check_against_pack(metalrender, offered)
        print(f"  MetalRender (separate zip): {'; '.join(metal_conflicts) or 'no conflicts'}")
        from_pack = from_pack or supplied
    label = args.label or git_commit()
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    main_zip = out / f"mystcraft2-tekxit4-fabric-ports-{label}.zip"
    metal_zip = out / f"metalrender-optional-{label}.zip"
    write_release_zip(main_zip, jars, release_readme(label, jars, from_pack, metal_zip.name))
    write_release_zip(metal_zip, metalrender,
                      METALRENDER_README.format(pack=RELEASE_PACK, label=label, jar=metalrender[0].name))
    sums = "".join(f"{sha256(path)}  {path.name}\n" for path in (main_zip, metal_zip))
    (out / f"SHA256SUMS-{label}").write_text(sums)
    print(f"Wrote {main_zip} ({len(jars)} jars) and {metal_zip}:")
    for jar in jars:
        print(f"  {jar.name}")
    print(sums, end="")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    fetch = commands.add_parser("fetch", help="download and unpack the official server into the cache")
    fetch.add_argument("--keep-archive", action="store_true", help="keep the zip after unpacking")
    fetch.set_defaults(function=command_fetch)
    build = commands.add_parser("build", help="build the Fabric ports, ICBM's Tekxit tests and the probe")
    build.add_argument("--ports", help="comma-separated port ids (default: all Fabric ports)")
    build.set_defaults(function=command_build)
    prepare = commands.add_parser("prepare", help="recreate the disposable server with the built jars")
    prepare.add_argument("--ports", help="comma-separated port ids (default: all Fabric ports)")
    prepare.add_argument("--dir", default=str(SERVER_DIR))
    prepare.add_argument("--no-probe", action="store_true", help="install the ports only")
    prepare.add_argument("--no-item-alchemy", action="store_true", help="leave Tekxit's optional Item Alchemy out")
    prepare.set_defaults(function=command_prepare)
    run = commands.add_parser("run", help="start the server, run the probe and GameTests, then stop")
    run.add_argument("--dir", default=str(SERVER_DIR))
    run.add_argument("--heap", default="6G")
    run.add_argument("--timeout", type=int, default=1200, help="seconds before sending stop")
    run.add_argument("--tests", help="comma-separated GameTest classes (default: the probe's and ICBM's Tekxit tests)")
    run.set_defaults(function=command_run)
    release = commands.add_parser("release", help="build the drop-in zip for a Tekxit 4 mods/ folder")
    release.add_argument("--pack", action="append",
                         help="pack to check dependencies against: a mods folder, a pack folder or a pack zip such "
                              "as 20.0.4Tekxit4.zip; repeatable (default: the server unpacked by fetch)")
    release.add_argument("--label", help="name suffix of the zips (default: the short commit)")
    release.add_argument("--out", default=str(RELEASE_DIR))
    release.add_argument("--skip-build", action="store_true", help="package the jars already built")
    release.set_defaults(function=command_release)
    args = parser.parse_args()
    return args.function(args) or 0


if __name__ == "__main__":
    sys.exit(main())
