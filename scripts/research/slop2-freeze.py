"""Freeze multi-route study data before measurement. No network/model calls."""
import hashlib, json, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
D=ROOT/'doc/research/slop2-20260926'
D.mkdir(parents=True,exist_ok=True)
if (D/'freeze.json').exists(): raise SystemExit('Already frozen')
def sha(t): return hashlib.sha256(t.encode()).hexdigest()
def save(n,x): (D/n).write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
old=json.loads((ROOT/'doc/research/slop-20260926/scenes.json').read_text(encoding='utf-8-sig'))
old={x['id']:x for x in old}
# Six independent pairs per family. First four development, last two holdout.
# Each positive/negative pair shares sourceGroup, never split across partitions.
pairs={
'negative_parallel':[
('不是查无此件，是这个编码段从去年三月起就冻结了。','这不是迟到，而是命运。这不是沉默，而是成长。这不是等待，而是觉醒。这不是结束，而是开始。'),
('不是她开的门，是门轴断了；木屑还在地上。','不是风在吹，是岁月在诉说。不是雨在下，是命运在叩门。不是灯在晃，是人生在起伏。'),
('不是我不去，是我不能离开值班台。她把交班记录摊开。','并非愤怒，只是觉醒。并非悲伤，只是成长。并非告别，只是启程。'),
('这不是新印的票，而是旧票上改了日期。墨还湿着。','他不是在看雨，而是在看人生。她不是在擦桌，而是在擦岁月。老人不是在锁门，而是在锁命运。'),
('与其说他拒绝，不如说他在等许可；桌上那张批条还没签字。','与其说是迟疑，不如说是蜕变。与其说是疲惫，不如说是成长。与其说是沉默，不如说是力量。'),
('她并没有把钱退回去。她退回的是装钱的空信封。','没有什么离别，只有新的开始；没有什么眼泪，只有成长的洗礼；没有什么挫折，只有命运的馈赠。')],
'dialogue_tail':[
('“你欠我的。”程遥的声音追上来。\n\n林砚没回头。“我知道。”','“走吧。”他的语气里带着坚定。“现在？”她的语气里带着惊讶。“现在。”他的语气里带着决绝。'),
('“别出声。”她贴着门说。走廊的脚步停在门外。','“你走。”她说，声音中充满了悲伤。“我等。”他说，声音中充满了坚定。“随你。”她说，声音中充满了无奈。'),
('“账不对。”他把算盘往她面前一推。“第三行，你再算。”','“我怕。”她说，这句话透露出恐惧。“我懂。”他说，这句话透露出理解。“谢谢。”她说，这句话透露出感激。'),
('“好。”她把钥匙递过去。过了一会又说：“备用的也给你。”','“好。”她轻声说道，话音透着温柔。“不。”他沉声说道，话音透着坚定。“罢了。”她低声说道，话音透着失落。'),
('“我没看见。”证人说得很响。门外记录员停下笔，示意他小声。','“不去。”她答，字字都是决绝。“等你。”他答，字字都是执着。“别等。”她答，字字都是悲伤。'),
('“吃了吗？”父亲仍旧这样问。她这回没敷衍：“还没有。”','“我知道了。”她说，显然已经明白。“我生气了。”他说，显然十分愤怒。“我很难过。”她说，显然内心悲伤。')],
'body_action':[
('书架不晃了。她用手推了推，又推了推。然后开始把书放回去。','她把书拿起又放下。他把杯拿起又放下。老人把笔拿起又放下。孩子把伞拿起又放下。'),
('他拧开瓶盖，发现封膜还在，便把瓶子放到灯下。','他嘴角微微上扬，眼神变得坚定。她嘴角微微上扬，眼神变得坚定。老人嘴角微微上扬，眼神变得坚定。'),
('她摸门闩，烫手。换用湿布裹住，才把它抽出来。','她垂下眼，又抬起眼。他垂下眼，又抬起眼。门边的人垂下眼，又抬起眼。'),
('他把钥匙递给她。她试了两次，换了个方向才插进去。','他手指微微颤抖，眼神闪过一丝复杂。她手指微微颤抖，眼神闪过一丝复杂。守卫手指微微颤抖，眼神闪过一丝复杂。'),
('她敲门一次，里面没声；又敲一次，门后才有人问是谁。','陈平捏了捏衣角又松开。赵禾捏了捏衣角又松开。孙叔捏了捏衣角又松开。'),
('他移开杯子才看见水圈，用纸擦干，再把杯子放回托盘。','她端起碗又搁下。他端起盘又搁下。弟弟端起盆又搁下。母亲端起杯又搁下。')],
'imagery':[
('线结收尾那个反方向，像一根细刺扎在指腹上。','目光像刀子划开夜色，沉默像刀子划开空气，回忆像刀子划开心口，告别像刀子划开岁月。'),
('敲钟人摸着裂口：铜皮卷起，像他手上没剪平的指甲。','空气仿佛凝固，时间像是停了下来。空气仿佛凝固，时间像是停了下来。空气仿佛凝固，时间像是停了下来。'),
('卖面的人挑起断线，笑说这东西煮过头了，跟店里的面一样。','忧愁是一片海，离别是一片海，沉默是一片海，等待也是一片海。'),
('台灯照在票根上，针孔投下一排蚂蚁大小的影子。','他的眼睛仿佛星辰，她的声音仿佛星辰，老人的沉默仿佛星辰，黑夜的风也仿佛星辰。'),
('他是裁缝，把蜿蜒水痕叫作这面墙缝歪的线脚。','话语宛若利刃刺破宁静，目光宛若利刃刺破岁月，脚步宛若利刃刺破命运。'),
('那堵墙像书页，被水一层一层掀开，露出底下旧字。','希望如同灯塔照亮人生，等待如同灯塔照亮岁月，告别如同灯塔照亮命运。')],
'abstract_summary':[
('她决定今天不去找。至少今天不去。','她收起书，所有的等待都有了答案。他关上门，所有的离别都有了意义。老人放下笔，所有的沉默都有了归宿。'),
('他在记录本写：水位降了两格，停泵，明早复查。','命运的齿轮开始转动。这一刻，他终于明白了成长的意义。命运的齿轮开始转动。这一刻，她终于明白了成长的意义。'),
('她算完路费，知道这次只能一个人去。另一张票退了。','她擦干杯子，这是成长的意义。他穿好外套，这是生命的真谛。老人收起报纸，这是命运的安排。'),
('信封留在桌上。他没有寄。这一晚到这里为止。','从此他踏上新的人生旅程。从此她开启新的命运篇章。从此老人迎来新的灵魂蜕变。'),
('这意味着仓库没有第二个出口。她在图上划掉了北门。','这意味着生命终将绽放。这意味着灵魂终将自由。这意味着岁月终将温柔。'),
('她把明天的值班牌翻过去，今天的事不留给接班人。','每一次关门都是新的开始，每一次起身都是灵魂的成长，每一次转头都是命运的馈赠。')],
'rhythm_repetition':[
('铜管深处传来闷响。掌心拍管壁，三快两慢，重复两次。','她把书放回去。她把杯放回去。她把笔放回去。她把伞放回去。她把刀放回去。'),
('灯灭。门开。她看见钥匙还在锁上，伸手拔了下来。','他看了一眼门。又看了一眼窗。再看了一眼钟。她看了一眼门。又看了一眼窗。再看了一眼钟。'),
('“三下。”她提醒。第一下没响。第二下钟摆脱开了。','他停了停。他想了想。他看了看。他等了等。她停了停。她想了想。她看了看。她等了等。'),
('父亲每次来都说“关窗”。这次窗早关了，他仍说。她把电热毯打开。','门边的人沉默了一瞬。窗边的人沉默了一瞬。桌边的人沉默了一瞬。走廊里的人沉默了一瞬。'),
('脚步停了。钥匙响了。她趁门还没开，把信压进账本。','她缓缓站起。她缓缓转头。她缓缓迈步。她缓缓伸手。她缓缓停住。'),
('一秒。两秒。计时器跳到三，他切断了电源。','他忽然明白了。她忽然明白了。老人忽然明白了。孩子忽然明白了。守卫忽然明白了。')]
}
real={'negative_parallel':('S2','“不是查无此件。”她抬头看程遥，“是这个编码段从去年三月起就冻结了。这封信在邮政系统里没存在过。”'),
'dialogue_tail':('S3',pairs['dialogue_tail'][0][0]),'body_action':('S5',pairs['body_action'][0][0]),
'imagery':('S2',pairs['imagery'][0][0]),'abstract_summary':('S5',pairs['abstract_summary'][0][0]),'rhythm_repetition':('S3',pairs['rhythm_repetition'][0][0])}
rows=[]
for family, family_pairs in pairs.items():
    for i,(negative,positive) in enumerate(family_pairs):
        source=None
        if i==0:
            source,negative=real[family]; assert negative in old[source]['text']
            # Controlled corruption retains the excerpt; added generic patterns are labeled.
            positive=negative+'\n'+positive
        group=source if source else f'{family}-pair-{i}'
        for label,text in [(False,negative),(True,positive)]:
            rows.append(dict(id=f'{family}-{i+1}-{int(label)}',family=family,split='development' if i<4 else 'holdout',
                sourceGroup=group,source=source,provenance=('controlled_perturbation' if label else 'real_output_excerpt') if source else 'agent_constructed',
                expectedIntervention=label,labelAuthority='agent_judgment',text=text,originalSha256=sha(text),genre='中文小说',tone='',
                reason='同构模板密集堆叠，信息推进不足' if label else '具体事实、人物意图或有功能的重复，应允许保留'))
save('samples.json',rows)

specs=[
('G1','悬疑','停用号段','许棠与档案员核对一张挂号联。号码在系统中显示去年三月起停用；登记时间是昨天下午四点三十七。许棠只知道这两项记录，尚不知道寄信者。场景结束时两人决定保留原联、申请调出原始登记表，不能直接找到寄信者。','停用不等于确定从未投递；不能变成已经识破寄信人。'),
('G2','悬疑','门后的回应','梁朔和叶宁在旧泵房门外。门内只回应三短两长的敲击，无法辨认回应者；水在门槛下渗出。两人没有钥匙。叶宁坚持先通知值班员，梁朔急于开门，最后同意先打电话但仍留在门边。','回应身份未知；不突然有钥匙、不自动开门、不确认里面是谁。'),
('G3','日常人物关系','没有寄出的地址','沈芸收拾准备归还给父亲的工具盒。盒内只有扳手和两枚垫圈。父亲在电话中说周日来取，她回答周日不在家，提出放门卫室。她知道自己周日其实在家，但不向父亲解释。结束时工具盒仍在她桌上。','父亲不知道她在回避；不寄出、不新增童年创伤或病情。'),
('G4','日常人物关系','两份早餐','周简与室友许禾昨晚为厨房清洁争执，原因仅是油锅未洗。今天周简想带早饭，打下道歉又删掉，改问对方吃什么。许禾只回复咸豆花。结束时周简把清洗锅的时间提前，二人尚未直接和好。','不可升级成恋爱或亲属关系；豆花不是豆浆；不得宣告和好。'),
('G5','幻想','第三次敲钟','学徒岑露守着一只裂纹铜钟。规则是日落后只能敲两次，第三次会让钟永久失声。师傅不在场。门外的人自称送药，但岑露没有见过他，也不知他说的是真是假。她已敲过一次，最后决定只敲第二次求值夜人查验。','不敲第三次；身份真假不揭示；不新增魔法能力或治愈钟。'),
('G6','幻想','换灯油','渡口掌灯人季遥与陌生商贩交易。她只有三枚铜币，灯油标价四枚；岸上只剩一盏还能燃一刻钟的灯。商贩愿意用一枚铜币加她一夜的值守换一小瓶灯油。她接受交易，但灯油还在对方手上，场景结束于她要求先看瓶口封蜡。','一枚铜币加一夜值守，不改价；尚未交油；不能凭封蜡知道油的真伪。')]
cards={
'悬疑':('围绕可核验物证推进；推断保留不确定性；对白不要替读者解释全部疑点；长短句随观察变化。','他把两张收据放到同一盏灯下。日期相同，纸上的折痕却对不上。“哪张先开的？”店员没有接话，先摸了一下抽屉。'),
'日常人物关系':('用生活事务承载关系变化；允许直述必要心理；对白保留回避和言外之意；不把普通动作逐句升华。','她把剩下的半袋米推到柜子里，给另一只杯子也倒了水。“几点回？”她问。那人已经穿好鞋，又蹲下去重系了一遍。'),
'幻想':('奇异规则通过当下选择呈现；物件可触可见；不额外解释世界史；允许叙述留白和适度修辞。','盐铺老板把秤盘上的羽毛拨开。“这不是钱。”来客收起羽毛，拿出一粒生锈的铃铛。老板这回关上了门。')}
tasks=[]
for n,(id_,genre,title,context,guard) in enumerate(specs,1):
    card,example=cards[genre]
    tasks.append(dict(id=id_,genre=genre,title=title,context=context+'\n硬边界：'+guard,
        hardBoundary=guard,styleCard=card,styleExample=example,styleSource='agent_constructed_unrelated_example',
        sceneOrder=n,minHan=550,maxHan=750,contextSha256=sha(context+'\n硬边界：'+guard)))
save('generation-tasks.json',tasks)
edits=[]
questions={
'S2':['编码段冻结时间是否仍为去年三月？','录入时间是否仍是昨天下午四点三十七？','周德海是否仍否认知道口子？','线结反向与独有手法的疑点是否保留？','砸台面与信封滑动的因果是否保留？'],
'S5':['是否仍是清晨五点五十？','信是否仍已经烧掉？','人物是否仍只决定今天不去找？','书架修复的木尺和螺丝是否保留？'],
'S6':['戏票是否仍压在玻璃杯底下？','消息是否仍删去票的事、改问早餐？','出门时间是否仍七点前？','豆浆改粥及饭团改菜包是否保留？']}
insertions=[
'\n\n“我明白。”她说，语气里带着理解。“先等。”他说，语气里带着坚定。“好。”她说，语气里带着顺从。',
'\n\n她把词典拿起又放下。她把木尺拿起又放下。她把铁盒拿起又放下。',
'\n\n这一刻，所有的等待都有了答案。这一刻，所有的沉默都有了归宿。这一刻，所有的选择都有了意义。']
for i,sid in enumerate(['S2','S5','S6']):
    for corrupt in [False,True]:
        id_=f'E{i+1 if not corrupt else i+4}'
        text=old[sid]['text']+(insertions[i] if corrupt else '')
        edits.append(dict(id=id_,source=sid,sourceGroup=sid,provenance='controlled_perturbation' if corrupt else 'real_model_output',
            text=text,originalSha256=sha(text),questions=questions[sid],
            perturbation=insertions[i] if corrupt else '',
            policy='仅将新增加的模板句视为可审查表达；事件仍来自这份冻结原文，不能借此修正原文既有问题。'))
save('editing-tasks.json',sorted(edits,key=lambda x:x['id']))
save('preservation-controls.json',[
dict(id='C1',original='她不知道钥匙在谁手上。',candidate='她知道钥匙在谁手上。',expected='fail',reason='否定/知情改变'),
dict(id='C2',original='票由姐姐保管。',candidate='她把票塞回自己的口袋。',expected='fail',reason='物件归属改变'),
dict(id='C3',original='他答得极硬，手指却停了半拍。',candidate='他答，手指停了半拍。',expected='voice_loss',reason='人物声音弱化，单独评价'),
dict(id='C4',original='她删掉消息。光标一闪一闪。她重新打字。',candidate='她删掉消息，重新打字。',expected='soft_change',reason='允许的节奏删并，不自动判硬失败'),
dict(id='C5',original='她锁门离开。\n柜子里还有一封未寄的信。',candidate='她锁门离开。',expected='fail',reason='遗漏线索/尾段'),
dict(id='C6',original='他也许看见了门后的人。',candidate='他看见了门后的人。',expected='fail',reason='确定性改变')])
save('freeze.json',dict(runId='slop2-20260926-v1',maximumCalls=40,plannedCalls=36,retryReserve=4,
 baselineCommit='3678b7a772d9916ac26bd74129098cb01c90d17f',model='deepseek-flash',
 source='new synthetic pairs plus six real excerpts and six controlled perturbations; agent labels only',
 files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(D.glob('*.json'))}))
print('Frozen 72 samples, 6 generation tasks, 6 editing tasks, 6 evaluator controls.')
