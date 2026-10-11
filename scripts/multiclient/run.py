#!/usr/bin/env python3
"""Multi-client savestate harness: one dedicated server plus several headless real clients.

Driven by scripts/multiclient-test.sh, which compiles the mod and writes build/multiclient/launch-*.json.
Each client runs net.swofty.dreammanhunt.test.multiclient.MultiClientDriver (gametest source set), which
only activates when -Ddreammanhunt.mc.role is set. Clients and this script coordinate through files in the
run directory: the script creates cmd-* files, clients answer with ready-/mutated-/done-<name> files and
append machine-readable MCT| lines to events-<name>.log.
"""
import json
import os
import random
import re
import signal
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CPUS = os.environ.get("DM_CPUS", "2-7,10,11,14-19")
SHOT_OFFSETS = (10, 30)

# name, role, ticks to walk forward during the mutate phase, degrees to turn
CLIENTS = [
	("runner", "runner", 20, 30),
	("hunter1", "hunter", 40, -45),
	("hunter2", "hunter", 60, 90),
	("vanillaish", "vanilla", 30, 60),
	("latecomer", "late", 25, 20),
]
AT_SAVE = ["runner", "hunter1", "hunter2", "vanillaish"]
MODDED_AT_SAVE = ["runner", "hunter1", "hunter2"]
BARRIER_PARTICIPANTS = ["runner", "hunter1", "hunter2", "latecomer"]

MAX_MEAN_ABS = 3.0
MAX_FRACTION_OVER_16 = 0.02
STRICT_MEAN_ABS = 0.05
STRICT_FRACTION_OVER_16 = 0.001

OPTIONS_TXT = """onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
pauseOnLostFocus:false
chatVisibility:2
narrator:0
soundCategory_master:0.0
renderDistance:6
simulationDistance:6
guiScale:2
maxFps:60
enableVsync:false
tutorialStep:none
realmsNotifications:false
"""


class Failure(Exception):
	pass


class Harness:
	def __init__(self, run_dir: Path):
		self.run_dir = run_dir
		self.procs = []
		self.server = None
		self.server_lines = []
		self.server_lock = threading.Lock()
		self.results = []
		self.client_launch = json.loads((ROOT / "build/multiclient/launch-runClientGameTest.json").read_text())
		self.server_launch = json.loads((ROOT / "build/multiclient/launch-runServer.json").read_text())
		self.xvfb = subprocess.check_output(["nix", "build", "--no-link", "--print-out-paths", "nixpkgs#xvfb-run"], text=True).strip() + "/bin/xvfb-run"

	def log(self, message):
		line = time.strftime("%H:%M:%S ") + message
		print(line, flush=True)
		with open(self.run_dir / "harness.log", "a") as f:
			f.write(line + "\n")

	def check(self, condition, message):
		self.results.append(("PASS" if condition else "FAIL", message))
		self.log(("PASS " if condition else "FAIL ") + message)
		return condition

	# ---- processes -------------------------------------------------------------------------------

	@staticmethod
	def descendants(root_pid):
		children = {}
		for entry in os.listdir("/proc"):
			if not entry.isdigit():
				continue
			try:
				with open(f"/proc/{entry}/stat") as f:
					stat = f.read()
				ppid = int(stat[stat.rindex(")") + 2:].split()[1])
				children.setdefault(ppid, []).append(int(entry))
			except (OSError, ValueError):
				continue
		found, stack = [], [root_pid]
		while stack:
			pid = stack.pop()
			for child in children.get(pid, []):
				found.append(child)
				stack.append(child)
		return found

	def cleanup(self):
		pids = []
		for proc in self.procs:
			pids.append(proc.pid)
			pids.extend(self.descendants(proc.pid))
		pids = list(dict.fromkeys(pids))
		for sig in (signal.SIGTERM, signal.SIGKILL):
			alive = []
			for pid in reversed(pids):
				try:
					os.kill(pid, sig)
					alive.append(pid)
				except ProcessLookupError:
					pass
			if not alive:
				break
			deadline = time.time() + 10
			while time.time() < deadline and any(Path(f"/proc/{p}").exists() for p in alive):
				time.sleep(0.2)
		for proc in self.procs:
			try:
				proc.wait(timeout=5)
			except subprocess.TimeoutExpired:
				pass
		left = [p for p in pids if Path(f"/proc/{p}").exists()]
		self.log(f"cleanup: signalled {len(pids)} processes, {len(left)} still present {left}")

	@staticmethod
	def jvm_args(launch, drop_prefixes):
		return [a for a in launch["jvmArgs"] if not a.startswith("@") and not any(a.startswith(p) for p in drop_prefixes)]

	def start_server(self, port):
		server_dir = self.run_dir / "server"
		(server_dir / "config").mkdir(parents=True)
		(server_dir / "eula.txt").write_text("eula=true\n")
		(server_dir / "server.properties").write_text("\n".join([
			"online-mode=false",
			f"server-port={port}",
			"level-type=minecraft\\:flat",
			"generate-structures=false",
			"spawn-protection=0",
			"view-distance=6",
			"simulation-distance=6",
			"enforce-secure-profile=false",
			"difficulty=peaceful",
			"pause-when-empty-seconds=0",
			"max-players=10",
			"white-list=false",
			"enforce-whitelist=false",
			"motd=dreammanhunt multiclient",
		]) + "\n")
		launch = self.server_launch
		cmd = ["taskset", "-c", CPUS, launch["java"], *self.jvm_args(launch, []), "-Xmx3G", "-cp", launch["classpath"], launch["mainClass"], "nogui"]
		self.server = subprocess.Popen(cmd, cwd=server_dir, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
			text=True, bufsize=1, start_new_session=True)
		self.procs.append(self.server)
		log = open(self.run_dir / "server.log", "w")

		def pump():
			for line in self.server.stdout:
				log.write(line)
				log.flush()
				with self.server_lock:
					self.server_lines.append(line.rstrip("\n"))

		threading.Thread(target=pump, daemon=True).start()
		self.wait_server(r"Done \(", 0, 180)

	def console(self, command):
		self.server.stdin.write(command + "\n")
		self.server.stdin.flush()

	def wait_server(self, pattern, start, timeout):
		regex = re.compile(pattern)
		deadline = time.time() + timeout
		while time.time() < deadline:
			if self.server.poll() is not None:
				raise Failure("server exited")
			with self.server_lock:
				lines = self.server_lines[start:]
			for offset, line in enumerate(lines):
				match = regex.search(line)
				if match:
					return match, start + offset + 1
			time.sleep(0.1)
		raise Failure(f"server log never matched {pattern!r}")

	def query(self, command, pattern):
		with self.server_lock:
			start = len(self.server_lines)
		self.console(command)
		match, _ = self.wait_server(pattern, start, 15)
		return match

	def entity_data(self, name, path):
		return self.query(f"data get entity {name} {path}", rf"{re.escape(name)} has the following entity data: (.*)$").group(1).strip()

	def snapshot_players(self, names):
		data = {}
		for name in names:
			data[name] = {
				"pos": [float(v.rstrip("d")) for v in self.entity_data(name, "Pos").strip("[]").split(",")],
				"inventory": self.entity_data(name, "Inventory"),
				"selected": self.entity_data(name, "SelectedItemSlot"),
			}
		return data

	def start_client(self, name, role, move_ticks, turn, port, display):
		game_dir = self.run_dir / name
		game_dir.mkdir(parents=True)
		(game_dir / "options.txt").write_text(OPTIONS_TXT)
		launch = self.client_launch
		jvm = self.jvm_args(launch, ["-Dfabric.client.gametest", "-Ddreammanhunt.test="])
		props = [
			f"-Ddreammanhunt.mc.name={name}",
			f"-Ddreammanhunt.mc.role={role}",
			f"-Ddreammanhunt.mc.dir={self.run_dir}",
			f"-Ddreammanhunt.mc.server=127.0.0.1:{port}",
			f"-Ddreammanhunt.mc.moveTicks={move_ticks}",
			f"-Ddreammanhunt.mc.turnDegrees={turn}",
		]
		if role == "vanilla":
			props.append("-Ddreammanhunt.disableNetworking=true")
		audio_conf = game_dir / "alsoft.conf"
		audio_conf.write_text(re.sub(r"(?m)^file\s*=.*$", f"file = {game_dir / 'audio.wav'}", (ROOT / "scripts/alsoft-gametest.conf").read_text()))
		cmd = ["env", "-u", "WAYLAND_DISPLAY", f"ALSOFT_CONF={audio_conf}", "taskset", "-c", CPUS, self.xvfb, "-n", str(display), "-s", "-screen 0 854x480x24",
			"steam-run", launch["java"], *jvm, "-Xmx2G", *props, "-cp", launch["classpath"], launch["mainClass"],
			"--username", name, "--gameDir", str(game_dir)]
		out = open(self.run_dir / f"client-{name}.out", "w")
		proc = subprocess.Popen(cmd, cwd=game_dir, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT, start_new_session=True)
		self.procs.append(proc)
		return proc

	# ---- coordination ----------------------------------------------------------------------------

	def touch(self, name):
		(self.run_dir / name).write_text("go\n")
		self.log(f"-> {name}")

	def wait_files(self, names, timeout, what):
		deadline = time.time() + timeout
		while time.time() < deadline:
			missing = [n for n in names if not (self.run_dir / n).exists()]
			if not missing:
				return
			for proc in self.procs:
				if proc.poll() is not None:
					raise Failure(f"a game process exited early (pid {proc.pid}, code {proc.returncode}) while waiting for {what}")
			for client, *_ in CLIENTS:
				dropped = [e for e in self.events(client) if e["kind"] == "disconnected"]
				if dropped:
					raise Failure(f"{client} was disconnected while waiting for {what}: {dropped[0]}")
			time.sleep(0.2)
		raise Failure(f"timed out waiting for {what}: missing {missing}")

	def events(self, name):
		path = self.run_dir / f"events-{name}.log"
		result = []
		if path.exists():
			for line in path.read_text().splitlines():
				parts = line.split("|")
				if len(parts) < 3 or parts[0] != "MCT":
					continue
				fields = {"name": parts[1], "kind": parts[2]}
				for part in parts[3:]:
					key, _, value = part.partition("=")
					fields[key] = value
				result.append(fields)
		return result

	def wait_event(self, name, kind, timeout):
		deadline = time.time() + timeout
		while time.time() < deadline:
			for event in self.events(name):
				if event["kind"] == kind:
					return event
			time.sleep(0.2)
		raise Failure(f"{name} never logged {kind}")

	def wait_shots(self, names, phase, timeout):
		files = [self.run_dir / "shots" / f"{n}-{phase}-{k}.png" for n in names for k in SHOT_OFFSETS]
		deadline = time.time() + timeout
		while time.time() < deadline:
			if all(f.exists() and f.stat().st_size > 0 for f in files):
				time.sleep(0.5)
				return
			time.sleep(0.2)
		raise Failure(f"missing {phase} screenshots: {[str(f.name) for f in files if not f.exists()]}")

	# ---- scenario --------------------------------------------------------------------------------

	def run(self):
		with socket.socket() as s:
			s.bind(("127.0.0.1", 0))
			port = s.getsockname()[1]
		display_base = random.randint(300, 900)
		self.log(f"run dir {self.run_dir}, port {port}, displays from {display_base}")
		self.start_server(port)
		self.log("server up")
		for command in ["gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
				"gamerule send_command_feedback false", "gamerule log_admin_commands false", "time set 6000", "weather clear"]:
			self.console(command)

		for index, (name, role, move, turn) in enumerate(CLIENTS):
			self.start_client(name, role, move, turn, port, display_base + index)
			time.sleep(2)
		self.wait_files([f"ready-{n}" for n in AT_SAVE], 300, "clients at save to join")
		self.log("initial clients joined")

		self.console("op runner")
		for index, name in enumerate(AT_SAVE):
			self.console(f"item replace entity {name} hotbar.0 with minecraft:bedrock {index + 1}")
			self.console(f"item replace entity {name} hotbar.4 with minecraft:barrier {index + 2}")
		time.sleep(3)

		self.touch("cmd-save")
		captures = {n: self.wait_event(n, "capture", 60) for n in MODDED_AT_SAVE}
		ids = {e["id"] for e in captures.values()}
		self.check(len(ids) == 1, f"every modded client captured the same state id {sorted(ids)}")
		state_id = next(iter(ids))
		saved = self.snapshot_players(AT_SAVE)
		self.wait_shots(MODDED_AT_SAVE, "cap", 60)
		self.log("capture-window screenshots taken")

		self.touch("cmd-join-late")
		self.wait_files(["ready-latecomer"], 180, "late hunter to join")
		self.console("execute as latecomer at @s run tp @s ~200 ~ ~")
		self.console("item replace entity latecomer hotbar.0 with minecraft:barrier 7")
		time.sleep(3)

		self.touch("cmd-mutate")
		for index, name in enumerate(AT_SAVE + ["latecomer"]):
			self.console(f"item replace entity {name} hotbar.0 with minecraft:barrier {index + 10}")
			self.console(f"item replace entity {name} hotbar.7 with minecraft:bedrock {index + 3}")
		self.wait_files([f"mutated-{n}" for n, *_ in CLIENTS], 60, "mutations")
		late_before = self.snapshot_players(["latecomer"])
		mutated = self.snapshot_players(AT_SAVE)
		for name in AT_SAVE:
			self.check(mutated[name] != saved[name], f"{name} server-side state actually changed before the restore")

		with self.server_lock:
			barrier_start = len(self.server_lines)
		self.touch("cmd-restore")
		self.wait_files([f"done-{n}" for n in BARRIER_PARTICIPANTS], 120, "post-resume windows")
		self.wait_shots(MODDED_AT_SAVE, "res", 30)
		restored = self.snapshot_players(AT_SAVE + ["latecomer"])

		self.verify(state_id, captures, saved, restored, late_before, barrier_start)
		for name, *_ in CLIENTS:
			wav = self.run_dir / name / "audio.wav"
			self.check(wav.exists(), f"{name} audio went to its own WAV file ({wav.stat().st_size if wav.exists() else 0} bytes)")

	def verify(self, state_id, captures, saved, restored, late_before, barrier_start):
		for name in MODDED_AT_SAVE + ["latecomer"]:
			kinds = [e["kind"] for e in self.events(name) if e.get("id") == state_id]
			expected = (["capture"] if name != "latecomer" else []) + ["begin", "apply", "ready_sent", "resume_packet", "resume"]
			present = [k for k in expected if k in kinds]
			self.check(present == expected, f"{name} saw {expected} for the state (got {kinds})")
			ordered = [k for k in kinds if k in expected and k != "capture"]
			self.check(ordered == [k for k in expected if k != "capture"], f"{name} saw begin/apply/ready/resume in order {ordered}")
		vanilla_kinds = {e["kind"] for e in self.events("vanillaish")}
		self.check(not vanilla_kinds & {"capture", "begin", "apply", "resume"}, f"vanilla-style client got no savestate payloads ({sorted(vanilla_kinds)})")
		self.check("latecomer" not in [e["name"] for e in captures.values()], "latecomer joined after the save")

		for name in MODDED_AT_SAVE:
			capture = next(e for e in self.events(name) if e["kind"] == "capture" and e.get("id") == state_id)
			resume = next(e for e in self.events(name) if e["kind"] == "resume" and e.get("id") == state_id)
			self.check(capture["base"] == resume["base"],
				f"{name}: Hud.tickCount at resume {resume['base']} == its own capture value {capture['base']} (player_tick {capture.get('player_tick')} -> {resume.get('player_tick')}, game_time {capture.get('game_time')} -> {resume.get('game_time')})")
		bases = {name: next(e for e in self.events(name) if e["kind"] == "capture")["base"] for name in MODDED_AT_SAVE}
		self.check(len(set(bases.values())) == len(bases), f"clients had distinct Hud.tickCount values at capture {bases}")

		with self.server_lock:
			lines = self.server_lines[barrier_start:]
		waiting = [l for l in lines if f"Restore {state_id} barrier: waiting for" in l]
		ready = [l for l in lines if f"Restore {state_id} barrier:" in l and " ready after " in l]
		resumed = [l for l in lines if f"Restore {state_id} barrier: resumed after" in l]
		self.check(len(waiting) == 1, f"server logged the barrier participants: {waiting}")
		if waiting:
			listed = sorted(n.strip() for n in waiting[0].split("waiting for [", 1)[1].rstrip("]").split(","))
			self.check(listed == sorted(BARRIER_PARTICIPANTS), f"barrier waited for exactly {sorted(BARRIER_PARTICIPANTS)} (got {listed})")
		order = [re.search(r"barrier: (\S+) ready after (\d+) ms", l).groups() for l in ready]
		self.log(f"ready order: {order}")
		self.check(sorted(n for n, _ in order) == sorted(BARRIER_PARTICIPANTS), "every participant reported ready")
		self.check(len(resumed) == 1 and "timed out: false" in resumed[0], f"barrier resumed without timing out: {resumed}")
		if resumed and ready:
			self.check(lines.index(resumed[0]) > max(lines.index(l) for l in ready), "server resumed only after the last ready")

		for name in AT_SAVE:
			before, after = saved[name], restored[name]
			distance = sum((a - b) ** 2 for a, b in zip(before["pos"], after["pos"])) ** 0.5
			self.check(distance < 0.01, f"{name} position restored server-side (off by {distance:.4f})")
			self.check(before["inventory"] == after["inventory"], f"{name} inventory restored server-side")
			self.check(before["selected"] == after["selected"], f"{name} selected slot restored server-side ({before['selected']} -> {after['selected']})")
		late_after = restored["latecomer"]
		distance = sum((a - b) ** 2 for a, b in zip(late_before["latecomer"]["pos"], late_after["pos"])) ** 0.5
		self.check(distance < 0.01, f"latecomer kept its position through the restore (moved {distance:.4f})")
		self.check(late_before["latecomer"]["inventory"] == late_after["inventory"], "latecomer kept its inventory through the restore")

		for name in BARRIER_PARTICIPANTS:
			events = self.events(name)
			on = [e for e in events if e["kind"] == "cut_marker_on"]
			off = [e for e in events if e["kind"] == "cut_marker_off"]
			resume = next((e for e in events if e["kind"] == "resume_packet" and e.get("id") == state_id), None)
			self.check(len(on) == 1 and len(off) == 1, f"{name} showed the cut marker exactly once ({len(on)} on, {len(off)} off)")
			if on and off and resume:
				before_count = int(on[0]["restores"])
				self.check(int(off[0]["restores"]) == before_count + 1,
					f"{name}: the first frame without the cut marker came after the resume ({off[0]['frozen_frames']} frozen frames)")
		vanilla_events = {e["kind"] for e in self.events("vanillaish")}
		self.check("cut_marker_on" not in vanilla_events, "vanilla-style client never showed the cut marker")
		frozen_shots = [self.run_dir / "shots" / f"{n}-frozen-0.png" for n in BARRIER_PARTICIPANTS]
		missing = [p.name for p in frozen_shots if not p.exists()]
		self.check(not missing, f"every participant took a frozen-frame screenshot (missing {missing})")
		marker_shots = [p for p in frozen_shots if p.exists()]
		marker_shots += [self.run_dir / "shots" / f"{n}-{phase}-{k}.png" for n in MODDED_AT_SAVE for phase in ("cap", "res") for k in SHOT_OFFSETS]
		output = subprocess.check_output(["taskset", "-c", CPUS, self.client_launch["java"], str(ROOT / "scripts/multiclient/ImageDiff.java"),
			"--green", *map(str, marker_shots)], text=True)
		for line in output.splitlines():
			_, file, band, rest = line.split("|")
			band, rest, name = float(band), float(rest), Path(file).name
			if "-frozen-" in name:
				self.check(band == 1.0 and rest < 0.01, f"{name}: solid green cut marker band ({band:.4f} of the band, {rest:.4f} elsewhere)")
			else:
				self.check(band < 0.01, f"{name}: no cut marker outside the freeze ({band:.4f} of the band)")

		pairs = []
		for name in MODDED_AT_SAVE:
			for k in SHOT_OFFSETS:
				pairs += [self.run_dir / "shots" / f"{name}-cap-{k}.png", self.run_dir / "shots" / f"{name}-res-{k}.png"]
			mutated_shot = self.run_dir / "shots" / f"{name}-mut-0.png"
			if mutated_shot.exists():
				pairs += [self.run_dir / "shots" / f"{name}-cap-{SHOT_OFFSETS[-1]}.png", mutated_shot]
		launch = self.client_launch
		output = subprocess.check_output(["taskset", "-c", CPUS, launch["java"], str(ROOT / "scripts/multiclient/ImageDiff.java"), *map(str, pairs)], text=True)
		for line in output.splitlines():
			parts = line.split("|")
			a, b = Path(parts[1]).name, Path(parts[2]).name
			if parts[3] == "size-mismatch":
				self.check(False, f"{a} vs {b}: size mismatch")
				continue
			mean, frac, worst = float(parts[5]), float(parts[6]), int(parts[7])
			if "-mut-" in b:
				self.check(mean > MAX_MEAN_ABS or frac > MAX_FRACTION_OVER_16,
					f"control: {a} vs {b} differ after mutation (mean {mean:.3f}, over16 {frac:.4%}, max {worst})")
			else:
				self.check(mean <= MAX_MEAN_ABS and frac <= MAX_FRACTION_OVER_16,
					f"{a} vs {b}: mean {mean:.3f}, over16 {frac:.4%}, max {worst}")
				if mean > STRICT_MEAN_ABS or frac > STRICT_FRACTION_OVER_16:
					self.log(f"WARN {a} vs {b} is above the strict level (mean {mean:.3f}, over16 {frac:.4%}): visible but small difference")

		for name in MODDED_AT_SAVE:
			capture = next(e for e in self.events(name) if e["kind"] == "capture" and e.get("id") == state_id)
			resume = next(e for e in self.events(name) if e["kind"] == "resume" and e.get("id") == state_id)
			seen_at_capture = sorted(o.split(":")[0] for o in capture.get("others", "").split(",") if o)
			seen_at_resume = sorted(o.split(":")[0] for o in resume.get("others", "").split(",") if o)
			self.check(seen_at_capture == seen_at_resume,
				f"{name}: the same other players are loaded on the client at capture and at resume ({seen_at_capture} -> {seen_at_resume})")
			moment = capture
			if name == "runner":
				moment = next(e for e in self.events(name) if e["kind"] == "save_requested")
			at_capture = sorted(o for o in moment.get("others", "").split(",") if o)
			at_resume = sorted(o for o in resume.get("others", "").split(",") if o)
			self.check(at_capture == at_resume,
				f"{name}: other players keep their client entity id and tickCount through the restore (name:id:tickCount {at_capture} -> {at_resume})")


def main():
	run_dir = ROOT / "build/multiclient" / time.strftime("run-%Y%m%d-%H%M%S")
	run_dir.mkdir(parents=True)
	harness = Harness(run_dir)

	def interrupted(signum, frame):
		raise KeyboardInterrupt

	signal.signal(signal.SIGTERM, interrupted)
	ok = False
	try:
		harness.run()
		ok = all(status == "PASS" for status, _ in harness.results)
	except (Failure, KeyboardInterrupt) as e:
		harness.log(f"ERROR {e!r}")
	finally:
		harness.cleanup()
	failed = [m for s, m in harness.results if s != "PASS"]
	harness.log(f"RESULT {'PASS' if ok else 'FAIL'}: {len(harness.results) - len(failed)} passed, {len(failed)} failed, run dir {run_dir}")
	sys.exit(0 if ok else 1)


if __name__ == "__main__":
	main()
