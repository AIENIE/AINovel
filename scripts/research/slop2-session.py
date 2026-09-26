"""Interactive research login. Secrets stay in memory; stdout contains no tokens.

Runs the documented SSO browser form/code flow with CSRF, normal TLS, and exact
redirect binding. Explicit line commands dispatch one experiment call at a time.
"""
import getpass
import http.cookiejar
import importlib.util
import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from html.parser import HTMLParser
from pathlib import Path

class Inputs(HTMLParser):
    def __init__(self):
        super().__init__(); self.values = {}
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "input" and a.get("type") == "hidden" and a.get("name"):
            self.values[a["name"]] = a.get("value", "")

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args): return None

opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl.create_default_context()),
    urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
base = "https://localainovel.testhut.top/api"
def send(url, data=None, headers=None):
    req = urllib.request.Request(url, data=data, headers=headers or {})
    try: return opener.open(req, timeout=30)
    except urllib.error.HTTPError as response: return response

username = input("SSO username: ")
password = getpass.getpass("SSO password: ")
state = str(uuid.uuid4())
response = send(base + "/v1/sso/login?next=%2Fworkbench&state=" + state)
login_url = response.headers["Location"]
parts = urllib.parse.urlparse(login_url)
assert parts.scheme == "https" and parts.netloc == "localuserservice.testhut.top"
params = urllib.parse.parse_qs(parts.query)
form = Inputs(); form.feed(send(login_url).read().decode("utf-8"))
payload = dict(form.values, username=username, password=password, keepDays="1",
               redirect=params["redirect"][0], state=state)
response = send("https://localuserservice.testhut.top/sso/login",
    urllib.parse.urlencode(payload).encode(), {"Content-Type": "application/x-www-form-urlencoded"})
del password, payload
assert response.code in (302,303), "SSO login did not redirect"
callback = urllib.parse.urlparse(response.headers["Location"])
q = urllib.parse.parse_qs(callback.query)
assert callback.scheme == "https" and callback.netloc == "localainovel.testhut.top" and q["state"][0] == state
response = send(base + "/v1/sso/session", json.dumps(dict(code=q["code"][0],redirect=params["redirect"][0])).encode(),
    {"Content-Type":"application/json"})
session = json.loads(response.read().decode("utf-8"))
if response.code != 200:
    print(json.dumps({"phase":"session","status":response.code,"code":session.get("code")}),flush=True)
    sys.exit(1)
os.environ["AINOVEL_RESEARCH_TOKEN"] = session["accessToken"]
response = send(base + "/v1/ai/models", headers={"Authorization":"Bearer "+session["accessToken"]})
models = json.loads(response.read().decode("utf-8"))
print(json.dumps({"phase":"authenticated-model-list","status":response.code,"models":models if response.code==200 else models.get("code")},ensure_ascii=False),flush=True)
if response.code != 200: sys.exit(1)
spec = importlib.util.spec_from_file_location("experiment",Path(__file__).with_name("slop2-client.py"))
experiment = importlib.util.module_from_spec(spec); spec.loader.exec_module(experiment)
print("READY",flush=True)
for line in sys.stdin:
    try:
        cmd = json.loads(line)
        if cmd.get("action") == "exit": break
        if cmd.get("action") == "call": experiment.call(cmd["scene"],cmd["phase"])
        elif cmd.get("action") == "apply": experiment.apply_patches(cmd["scene"])
        else: print("Unknown command",flush=True)
    except Exception as error:
        print(json.dumps({"errorType":type(error).__name__}),flush=True)
    print("READY",flush=True)
os.environ.pop("AINOVEL_RESEARCH_TOKEN",None)
