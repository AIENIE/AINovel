"""Freeze scene-goal study inputs offline. Run before Java adapter, then --seal."""
import hashlib, json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'doc/research/slop3-20260926'
def save(name,value):
 (DATA/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def sha(text): return hashlib.sha256(text.encode()).hexdigest()
HEADER='本节叙事质量目标（请重点体现以下要素）：'
REVIEW='''你是中文小说评审。四份匿名初稿来自相同任务。先逐份列举全部新增关键断言，引用原句，追溯到冻结上下文或标明无来源；不能仅回答问题。
事实、人物知情、否定、物件归属、线索及确定性为硬约束；普通环境、即时感官与不改变因果的动作可自由补充。别把所有未写明细节都判错，也别因文字自然而放过新增往事、规则或结局。
逐份评价事实知情、声音意图、场景完成、自然度、节奏重复；不用总分。完整阅读，不略过尾段。独立校准案例与正文隔离，每例判断是否破坏硬约束并解释。
输出JSON：{"outputs":[{"label":"甲","claims":[{"quote":"...","source":"...","verdict":"supported|ordinary|uncertain|failure"}],"hard":"pass|fail|uncertain","dimensions":{"voice":"...","goal":"...","naturalness":"...","rhythm":"..."}}],"calibration":[{"id":"C1","verdict":"...","reason":"..."}]}。'''
if '--seal' in sys.argv:
 protocol=json.loads((DATA/'protocol.json').read_text(encoding='utf8'))
 assert not protocol.get('hashes'), 'Already sealed'
 protocol['hashes']={n:sha((DATA/n).read_text(encoding='utf8')) for n in ['tasks.json','requests.json','calibration.json']}
 save('protocol.json',protocol)
 save('seal.json',dict(protocolSha256=sha((DATA/'protocol.json').read_text(encoding='utf8'))))
 print('Sealed input hashes; no paid calls.')
 sys.exit()
assert not DATA.exists(), 'Do not overwrite a frozen study'
DATA.mkdir(parents=True)
old=json.loads((ROOT/'doc/research/slop2-20260926/generation-tasks.json').read_text(encoding='utf8'))
goals=[
 ['围绕停用号段与登记时间核查，推进到保留原联并申请原始登记表。','让疑点通过记录对照和必要问答呈现；保持寄信者与实际投递情况未知。'],
 ['呈现梁朔急于开门与叶宁先通知值班员的分歧，推进到先打电话但仍留门边。','让门内敲击、渗水与两人的即时动作承载紧迫感，保留回应者身份未知。'],
 ['围绕归还工具盒的电话安排，呈现沈芸回避见面而不向父亲解释。','用话语选择和收拾工具盒的动作表达回避；允许关系停留原处，不追加往事。'],
 ['呈现删去道歉、改问早餐及提前洗锅的行动，保持两人尚未直接和好。','通过生活事务和简短回复承载试探，不把咸豆花的回复解释为明确原谅。'],
 ['让岑露在已有敲钟规则下决定只用第二次求值夜人查验。','通过眼前铜钟与门外来人的应对呈现选择；保留送药说法真假未知。'],
 ['按一枚铜币加一夜值守接受交易，推进到要求先看封蜡而尚未交油。','通过议价与查验要求表达谨慎；不凭外观断定灯油真假。'],
 ['对照借用表和断电记录，推进到申请原始登记页，保留是否有人进入房间未知。','将两条时间记录分开陈述，允许提出疑点而不把时间相近写成已证实的因果。'],
 ['围绕归还雨伞确认放置位置，呈现林澄暂不解释缺席的选择。','让简短消息与放伞动作承载分寸，允许关系暂时不变，不追加冲突或和解。']]
extra=[
 dict(id='G7',genre='悬疑',title='断电前的借用表',sceneOrder=7,context='顾遥和管理员核对会议室记录。借用表写着周二十八点十分快速登记了一次借用，但签名栏空白；设备日志只记录十八点十二分断电。顾遥未进入会议室，二人都不知道借用人是谁，也不知道当时是否有人实际进入。顾遥希望保留两条记录的差别，管理员愿意申请纸质原始登记页。场景结束于两人提出申请，尚未拿到原页。\n硬边界：借用登记不等于实际进入；时间先后不证明人为断电；不补签名、监控或新目击证人。'),
 dict(id='G8',genre='日常人物关系',title='伞放在鞋架旁',sceneOrder=8,context='林澄把向朋友陈棠借来的深蓝雨伞送回公寓门口。陈棠发消息说自己尚未到家，允许把伞放在门外鞋架右侧。林澄昨天没有参加两人的晚饭约定，陈棠还不知道原因；林澄这次只想还伞，暂时不解释。她按要求放好伞，回复“放好了”。结束时两人尚未见面，陈棠没有回复这条消息。\n硬边界：不补缺席原因或既往争执；不把未回复断定成生气或原谅；伞归陈棠，不改颜色及放置位置。')]
tasks=[]
intents=['核查而非揭谜','在紧迫中先寻求值班员','回避见面但完成归还安排','以事务试探但不直接道歉和好','不冒第三次敲钟风险地求查验','接受明确条件但先看封蜡','核对两记录而不先下结论','只还伞、暂不解释缺席']
for i,t in enumerate(old+extra):
 task={k:t[k] for k in ['id','genre','title','sceneOrder','context']}
 task.update(source='historical_agent_constructed_regression' if i<6 else 'new_agent_constructed',sourcePath='doc/research/slop2-20260926/generation-tasks.json' if i<6 else 'agent offline, this study',sceneGoals=goals[i],goalSource='agent_offline_restatement_of_frozen_task',intent=intents[i],minHan=550,maxHan=750,
 ordinaryDetails='允许不改变事实与因果的环境光声、手势、即时感官、普通办公或生活物件；不允许新增关键往事、身份、物件证据、世界规则、确定性结论或完成尚未完成的事件。',
 endpointAndUnknowns='以上 context 的结束位置、未知事项和硬边界为逐字来源；不隐含额外作者秘密。')
 task['contextSha256']=sha(task['context']); tasks.append(task)
save('tasks.json',tasks)
save('calibration.json',[
 dict(id='C1',source='她没有打开门。',candidate='她打开了门。',expected='failure:negation'),
 dict(id='C2',source='雨伞属于陈棠。',candidate='林澄收好自己的雨伞。',expected='failure:ownership'),
 dict(id='C3',source='借用表签名栏为空；设备日志十八点十二分断电。任务要求保留两条记录。',candidate='设备日志十八点十二分断电，她据此提出申请。',expected='failure:critical_omission'),
 dict(id='C4',source='尚不能确定当时有人进入。',candidate='当时肯定有人进入。',expected='failure:certainty')])
order=[]
for rep in [1,2]:
 for i,sid in enumerate(['G1','G3','G5','G7','G2','G4','G6','G8']):
  arms=['A','B'] if (i+rep)%2 else ['B','A']
  order.append(dict(pair=f'{sid}-{rep}',calls=[f'{sid}-{a}{rep}' for a in arms]))
save('protocol.json',dict(runId='slop3-20260926-v1',maximumCalls=40,generationCap=32,reviewCap=4,retryCap=4,
 model='deepseek-flash',sampling='Application does not expose seed/temperature; different idempotency keys are not controlled random seeds.',
 order=order,reviewScenes=['G1','G3','G5','G7'],reviewPrompt=REVIEW,
 failureClasses=['new_critical_fact','knowledge_or_certainty','event_or_endpoint','ownership_or_quantity','critical_omission'],
 stop='Within either arm, two outputs with the same hard-failure class (including different replicates) stop that arm before another pair. Each output counts at most once per class. Uncertain cases do not increment. Missing comparisons never count as wins.',
 eligibility=dict(completeB=16,hardFailuresB=0,pairWinsB=10,scenesBothNonWorseAndOneBetter=6,allClaimsJudged=True,noSystematicVoiceLoss=True,lengthAdherenceNoWorse=True),
 evaluation='Agent reads each pair in full before next pair and before anonymous model review. Model reviews only complete four-output scenes; incomplete scenes are explicitly skipped. This is not human G2 or product L4.',
 isolation='Only per-scene frozen context, no author secrets, no style profiles or other scene data. Shared calibration is review-only and segregated.',
 retry='Only manually authorized retries after ledger-confirmed technical failure; same completed key never dispatched again; STARTED or ambiguous ledger blocks all dispatch. No result-directed retries.',hashes={}))
print('Frozen 8 tasks and protocol; generate prompts then --seal before any call.')
