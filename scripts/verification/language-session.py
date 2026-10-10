"""Normal local SSO + explicitly dispatched language acceptance calls.

Tokens stay in process memory. TLS/CSRF/redirect binding remain enabled. Model calls
use the single language-20261007-v1 server ledger; this client never retries a model
request or creates another budget. Test-only outputs are saved under doc/verification.
"""
import getpass, html, http.cookiejar, importlib.util, json, ssl, sys, time, urllib.error, urllib.parse, urllib.request, uuid
from html.parser import HTMLParser
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/"doc/verification/language-20261007"
BASE="https://localainovel.testhut.top/api"
class Inputs(HTMLParser):
    def __init__(self): super().__init__();self.values={}
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=="input" and a.get("type")=="hidden" and a.get("name"): self.values[a["name"]]=a.get("value","")
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args): return None
opener=urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl.create_default_context()),urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),NoRedirect())
def send(url,data=None,headers=None,method=None):
    try: return opener.open(urllib.request.Request(url,data=data,headers=headers or {},method=method),timeout=180)
    except urllib.error.HTTPError as response: return response
def save(name,value):
    OUT.mkdir(parents=True,exist_ok=True);(OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
def read(name,default=None):
    p=OUT/name;return json.loads(p.read_text(encoding="utf-8")) if p.exists() else default
def api(path,payload=None,method=None,key=None,expected=200):
    headers={"Authorization":"Bearer "+token,"Content-Type":"application/json"}
    if key:headers["Idempotency-Key"]=key
    response=send(BASE+path,None if payload is None else json.dumps(payload,ensure_ascii=False).encode(),headers,method)
    raw=response.read().decode("utf-8");data=json.loads(raw) if raw else None
    if response.code!=expected:raise RuntimeError(json.dumps({"path":path,"status":response.code,"error":data},ensure_ascii=False))
    return data
def poll(operation):
    for _ in range(180):
        result=api("/v1/ai-operations/"+operation)
        if result["status"] not in ("QUEUED","RUNNING","STREAMING"):return result
        time.sleep(2)
    raise TimeoutError("Operation still running; read it before issuing any further request")
def cases():
    corpus={r["id"]:r for r in json.loads((ROOT/"doc/research/slop-20260926/reading-corpus.json").read_text(encoding="utf-8-sig"))}
    items={k:corpus[k]["text"] for k in ("S5","S6","fast:3","fast:6")}
    items["normal"]="她从桌上拿起杯子，倒了半杯水，喝完放回原处。\n\n“走吗？”\n\n“等我拿上钥匙。”\n\n他从挂钩上取下钥匙，关好窗，跟着她出了门。"
    items["repeated"]="她把写着‘A & B 😀’的纸条摊开，照着上面的字读了一遍。\n\n他关上门。\n\n走廊尽头又有一扇门，他走过去，检查了门锁。\n\n他关上门。"
    # Whole paragraphs exceed the old 7k cutoff; the final paragraph must remain covered.
    items["long-tail"]="\n\n".join(["她逐页核对登记册，把已经确认的日期写在纸上。遇到看不清的字，她先记下页码，等同事回来再问。" for _ in range(165)])+"\n\n可‘找’这个动作还在，不拉也在。"
    return items
def seed():
    state=read("local-fixtures.json",{})
    if not state.get("storyId"):
        matches=[s for s in api("/v1/story-cards") if s["title"]=="语言自然度本地验收 20261007"]
        story=matches[0] if matches else api("/v1/stories",{"title":"语言自然度本地验收 20261007","synopsis":"仅用于语言验收。H2隐藏资料标记：不得发送本说明及未公开计划。","genre":"现实","tone":"自然"})
        state["storyId"]=story["id"];save("local-fixtures.json",state)
    if not state.get("outlineId"):
        outlines=api("/v1/story-cards/"+state["storyId"]+"/outlines")
        outline=outlines[0] if outlines else api("/v1/story-cards/"+state["storyId"]+"/outlines",{"title":"固定验收场景"})
        state["outlineId"]=outline["id"];save("local-fixtures.json",state)
    if not state.get("scenes"):
        state["scenes"]={k:str(uuid.uuid5(uuid.NAMESPACE_URL,"language-20261007/"+k)) for k in cases()}
        api("/v1/outlines/"+state["outlineId"],{"title":"固定验收场景","chapters":[{"id":str(uuid.uuid5(uuid.NAMESPACE_URL,"language-20261007/chapter")),"title":"语言验收","order":1,"scenes":[{"id":v,"title":k,"summary":"冻结正文用于语言检查，不增加剧情。","order":i+1} for i,(k,v) in enumerate(state["scenes"].items())]}]},"PUT")
        save("local-fixtures.json",state)
    state.setdefault("manuscripts",{})
    for name,text in cases().items():
        if name in state["manuscripts"]:continue
        m=api("/v1/outlines/"+state["outlineId"]+"/manuscripts",{"title":"语言验收 · "+name},key=str(uuid.uuid5(uuid.NAMESPACE_URL,"language-20261007/manuscript/"+name)))
        body="".join("<p>"+html.escape(p)+"</p>" for p in text.split("\n\n"))
        m=api("/v1/manuscripts/"+m["id"]+"/sections/"+state["scenes"][name],{"content":body,"expectedVersion":m["version"],"expectedBranchId":m.get("currentBranchId")},"PUT")
        api("/v2/manuscripts/"+m["id"]+"/quality-runs/language/settings",{"generationStandard":True,"checkAfterGeneration":True},"PUT")
        state["manuscripts"][name]=m["id"];save("local-fixtures.json",state)
    save("frozen-diagnostic-cases.json",cases());print(json.dumps({"seeded":list(state["manuscripts"]),"modelCalls":0}),flush=True)
def generation(ids):
    prompts=read("frozen-prompts-v2.json")["items"]
    for identity in ids:
        name="generation-"+identity+".json"
        if read(name) is not None:print(identity+" already saved",flush=True);continue
        item=next(p for p in prompts if p["id"]==identity)
        result=api("/v1/ai/chat",item["request"],key="language-20261007:"+identity)
        save(name,{"id":identity,"response":{k:v for k,v in result.items() if k in ("role","content","usage")}});print(json.dumps({"generation":identity,"characters":len(result["content"]),"usage":result.get("usage")},ensure_ascii=False),flush=True)
def report(name):
    state=read("local-fixtures.json");m=state["manuscripts"][name];scene=state["scenes"][name]
    result=api("/v2/manuscripts/"+m+"/quality-runs/language?sceneId="+scene)
    if result:save("diagnosis-"+name.replace(":","-")+".json",result[0])
    return result[0] if result else None
def diagnose(names):
    state=read("local-fixtures.json")
    for name in names:
        m=api("/v1/manuscripts/"+state["manuscripts"][name]);path="/v2/manuscripts/"+m["id"]+"/scenes/"+state["scenes"][name]+"/quality-runs/language/operations"
        op=api(path,{"expectedBranchId":m.get("currentBranchId"),"expectedVersion":m["version"]},expected=202)
        result=poll(op["operationId"]);save("operation-diagnosis-"+name.replace(":","-")+".json",result)
        data=report(name);print(json.dumps({"diagnosis":name,"operation":result["status"],"status":data["status"],"issues":len(data["issues"]),"coverage":data["coverage"]},ensure_ascii=False),flush=True)
        if result["status"]!="SUCCEEDED":break
def suggest(name,issue):
    data=report(name);path="/v2/manuscripts/"+data["manuscriptId"]+"/quality-runs/language/"+data["id"]+"/issues/"+issue+"/suggestions/operations"
    op=api(path,{},expected=202);result=poll(op["operationId"]);save("operation-patch-"+name.replace(":","-")+"-"+issue+".json",result);data=report(name)
    print(json.dumps({"suggestion":name,"issue":issue,"operation":result["status"],"patches":data["patches"]},ensure_ascii=False),flush=True)

def prepare_h2():
    state=read("local-fixtures.json");scene=state["scenes"]["normal"]
    if not state.get("characterId"):
        characters=api("/v1/story-cards/"+state["storyId"]+"/character-cards")
        character=next((c for c in characters if c["name"]=="周宁"),None)
        if character is None:character=api("/v1/story-cards/"+state["storyId"]+"/characters",{"name":"周宁","synopsis":"验收用视角人物","details":"H2-CARD-SECRET-LANGUAGE-20261007：未获准进入正文的角色资料。"})
        state["characterId"]=character["id"];save("local-fixtures.json",state)
    outline=api("/v1/outlines/"+state["outlineId"])
    for chapter in outline["chapters"]:
        for item in chapter["scenes"]:
            if item["id"]==scene:item["planning"]={**(item.get("planning") or {}),"minHan":400,"maxHan":1000}
    api("/v1/outlines/"+outline["id"],{k:outline.get(k) for k in ("title","worldId","planning","chapters")},"PUT")
    for mode in ("fast","crafted"):
        name="H2-"+mode
        if name not in state["manuscripts"]:
            m=api("/v1/outlines/"+state["outlineId"]+"/manuscripts",{"title":"语言后台流程 · "+mode},key=str(uuid.uuid5(uuid.NAMESPACE_URL,"language-20261007/"+name)))
            state["manuscripts"][name]=m["id"];state["scenes"][name]=scene;save("local-fixtures.json",state)
        mid=state["manuscripts"][name];api("/v2/manuscripts/"+mid+"/versions")
        m=api("/v1/manuscripts/"+mid);path="/v2/manuscripts/"+mid+"/branches/"+m["currentBranchId"]+"/narrative/context"
        current=api(path)
        if not current["document"]:
            document={"policy":{"perspective":"LIMITED_THIRD","allowInner":True,"viewpointByScene":{scene:state["characterId"]}},"grants":[],"entries":[
                {"id":str(uuid.uuid5(uuid.NAMESPACE_URL,name+"-plan")),"kind":"PLAN","text":"周宁按约把红伞还给邻居。邻居核对伞柄姓名，确认是自己的伞。两人只作简短对白。只写当前归还过程，不补往事或揭示其他人物的想法。","fromSceneId":scene,"characterIds":[state["characterId"]],"narratorVisible":True,"dependencyRecordIds":[],"stale":False},
                {"id":str(uuid.uuid5(uuid.NAMESPACE_URL,name+"-hidden")),"kind":"BACKGROUND","text":"H2-SECRET-LANGUAGE-20261007：未公开的以后剧情。","fromSceneId":state["scenes"]["repeated"],"characterIds":[],"narratorVisible":False,"dependencyRecordIds":[],"stale":False}]}
            api(path,{"expectedManuscriptVersion":current["manuscriptVersion"],"expectedCanonRevision":current["canonRevision"],"expectedRevision":current["revision"],"expectedSettingsRevision":current["settingsRevision"],"enabled":True,"document":document},"PUT",key="language-20261007-config-"+mode)
        preview=api(path+"/preview?sceneId="+scene+"&view=SCENE&budget=24000")
        assert "H2-SECRET-LANGUAGE-20261007" not in preview["content"] and "H2-CARD-SECRET-LANGUAGE-20261007" not in preview["content"]
        save("h2-preview-"+mode+".json",preview)
    print(json.dumps({"preparedH2":True,"modelCalls":0}),flush=True)

def pipeline(mode):
    assert mode in ("fast","crafted")
    state=read("local-fixtures.json");name="H2-"+mode;mid=state["manuscripts"][name];scene=state["scenes"][name]
    accepted=api("/v1/manuscripts/"+mid+"/scenes/"+scene+"/generate/operations?mode="+mode,{},key="language-20261007-pipeline-"+mode,expected=202)
    generated=poll(accepted["operationId"]);save("pipeline-generation-"+mode+".json",generated)
    m=api("/v1/manuscripts/"+mid);save("pipeline-body-"+mode+".json",m)
    data=report(name)
    save("pipeline-handoff-"+mode+".json",{"generationStatus":generated["status"],"bodyVersion":m["version"],"languageStatusAfterBodyDelivery":None if data is None else data["status"],"diagnosisOperation":None if data is None else data["operationId"]})
    if data and data["operationId"]:
        diagnosis=poll(data["operationId"]);save("pipeline-diagnosis-operation-"+mode+".json",diagnosis);data=report(name)
    print(json.dumps({"pipeline":mode,"generationStatus":generated["status"],"languageStatus":None if data is None else data["status"],"bodyVersion":m["version"]}),flush=True)

if __name__=="__main__":
    username=input("SSO username: ");password=getpass.getpass("SSO password: ");state=str(uuid.uuid4())
    response=send(BASE+"/v1/sso/login?next=%2Fworkbench&state="+state);login_url=response.headers["Location"]
    parts=urllib.parse.urlparse(login_url);assert parts.scheme=="https" and parts.netloc=="localuserservice.testhut.top"
    params=urllib.parse.parse_qs(parts.query);form=Inputs();form.feed(send(login_url).read().decode("utf-8"))
    payload=dict(form.values,username=username,password=password,keepDays="1",redirect=params["redirect"][0],state=state)
    response=send("https://localuserservice.testhut.top/sso/login",urllib.parse.urlencode(payload).encode(),{"Content-Type":"application/x-www-form-urlencoded"});del password,payload
    assert response.code in (302,303),"SSO login did not redirect"
    callback=urllib.parse.urlparse(response.headers["Location"]);q=urllib.parse.parse_qs(callback.query)
    assert callback.scheme=="https" and callback.netloc=="localainovel.testhut.top" and q["state"][0]==state
    response=send(BASE+"/v1/sso/session",json.dumps(dict(code=q["code"][0],redirect=params["redirect"][0])).encode(),{"Content-Type":"application/json"})
    assert response.code==200,"SSO exchange failed";token=json.loads(response.read().decode("utf-8"))["accessToken"]
    print(json.dumps({"authenticated":True,"models":api("/v1/ai/models")},ensure_ascii=False),flush=True)
    print("READY",flush=True)
    for line in sys.stdin:
        try:
            command=json.loads(line);action=command["action"]
            if action=="exit":break
            if action=="seed":seed()
            elif action=="generation":generation(command["ids"])
            elif action=="diagnose":diagnose(command["names"])
            elif action=="suggest":suggest(command["name"],command["issue"])
            elif action=="prepare-h2":prepare_h2()
            elif action=="pipeline":pipeline(command["mode"])
            elif action=="workflow":
                spec=importlib.util.spec_from_file_location("language_workflow",Path(__file__).with_name("language-workflow.py"))
                module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
                print(json.dumps(module.run(api,read,save,command),ensure_ascii=False),flush=True)
            elif action=="report":print(json.dumps(report(command["name"]),ensure_ascii=False),flush=True)
            elif action=="api":
                result=api(command["path"],command.get("payload"),command.get("method"),command.get("key"),command.get("expected",200))
                if command.get("save"):save(command["save"],result)
                print(json.dumps(result,ensure_ascii=False),flush=True)
        except Exception as error:print(json.dumps({"errorType":type(error).__name__,"message":str(error)},ensure_ascii=False),flush=True)
        print("READY",flush=True)
