"""GitHub REST API helper for SupplyBeam: token from git credential fill, proxy 7897."""
import json
import subprocess
import sys
import urllib.request
import urllib.error

PROXY = "http://127.0.0.1:7897"
API = "https://api.github.com"
UPLOADS = "https://uploads.github.com"
REPO = "TakaraMiyuki/SupplyBeam"


def token():
    out = subprocess.run(
        ["git", "credential", "fill"],
        input="protocol=https\nhost=github.com\n",
        capture_output=True, text=True, check=True).stdout
    for line in out.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1]
    raise RuntimeError("no github token in credential store")


def request(method, url, tok, payload=None, raw=None, content_type="application/json"):
    data = raw if raw is not None else (json.dumps(payload).encode() if payload is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {tok}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "supplybeam-release-script")
    if data is not None:
        req.add_header("Content-Type", content_type)
    handler = urllib.request.ProxyHandler({"http": PROXY, "https": PROXY})
    opener = urllib.request.build_opener(handler)
    try:
        with opener.open(req, timeout=120) as resp:
            body = resp.read()
            return resp.status, json.loads(body) if body and content_type == "application/json" else body
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        return e.code, body


if __name__ == "__main__":
    cmd = sys.argv[1]
    t = token()
    if cmd == "get-repo":
        status, body = request("GET", f"{API}/repos/{REPO}", t)
        print(status, body.get("full_name") if isinstance(body, dict) else body)
    elif cmd == "create-repo":
        status, body = request("POST", f"{API}/user/repos", t, {
            "name": "SupplyBeam",
            "description": "Minecraft Java 26.2 / NeoForge 26.2 supply drop mod inspired by ARK: colored supply beams randomly appear in the Overworld, rarity by beam color, an octahedral crate descends along the beam. Optional Manhunt integration. 方舟风格空投光柱补给模组。",
            "homepage": "",
            "private": False,
            "has_issues": True, "has_projects": False, "has_wiki": False,
        })
        print(status, body.get("full_name") if isinstance(body, dict) else body)
    elif cmd == "create-release":
        payload = json.load(open(sys.argv[2], encoding="utf-8"))
        status, body = request("POST", f"{API}/repos/{REPO}/releases", t, payload)
        print(status, body.get("id") if isinstance(body, dict) else body)
    elif cmd == "upload-asset":
        release_id, path, name = sys.argv[2], sys.argv[3], sys.argv[4]
        data = open(path, "rb").read()
        status, body = request(
            "POST", f"{UPLOADS}/repos/{REPO}/releases/{release_id}/assets?name={name}",
            t, raw=data, content_type="application/java-archive")
        print(status, body.get("browser_download_url") if isinstance(body, dict) else body)
