#!/usr/bin/env python3
"""Fetch the official Tekxit 4 server, install the Fabric ports into a disposable copy and probe it.

  fetch    download and verify the server pack and the Fabric GameTest API into the cache
  build    build every Fabric port, ICBM's Tekxit test jar and the boot probe
  prepare  recreate the disposable server with the built jars installed
  run      start it, run the probe's checks and GameTests, stop it and report

The pack is downloaded from tekxit.lol and never committed. Minecraft's EULA is accepted only when
the operator sets MINECRAFT_EULA=true; nothing here accepts it on their behalf.
"""
import argparse
import hashlib
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
NOT_PRODUCTION = ("-sources", "-dev", "-named", "-javadoc", "-tests", "smoke")
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
    return sorted(jar for jar in libs.glob("*.jar") if not any(tag in jar.stem for tag in NOT_PRODUCTION))


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
    args = parser.parse_args()
    return args.function(args) or 0


if __name__ == "__main__":
    sys.exit(main())
