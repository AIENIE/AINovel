-- AINovel V2: slop_patterns table
-- Seed data derived from ai_novel_ai_taste_research/algorithms/10-pattern-dictionary-v1.md
-- Used by SlopPatternSamplingService to inject negative constraints in crafted generation mode.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `slop_patterns` (
  `id`       binary(16)   NOT NULL,
  `category` varchar(32)  NOT NULL COMMENT 'PHRASE | BODY_ACTION | IMAGERY | ENDING_CLICHE | NARRATIVE_MECHANIC',
  `pattern`  varchar(255) NOT NULL COMMENT '供 prompt 注入的禁用表达描述，面向模型可理解的自然语言',
  `weight`   tinyint      NOT NULL DEFAULT 2 COMMENT '1-5, 越高越优先被采样',
  `enabled`  bit(1)       NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_slop_patterns_category` (`category`),
  KEY `idx_slop_patterns_enabled`  (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- 类别 PHRASE：固定句式 / 语气模板 (A + B 系列)
-- ─────────────────────────────────────────────
INSERT INTO `slop_patterns` VALUES
  (UNHEX(REPLACE(UUID(),'-','')), 'PHRASE', '不是……而是……（高密度时形成解释腔，单次仍需慎用）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'PHRASE', '不是……不是……而是……（多重否定铺垫，模型式层层递进）', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'PHRASE', '没有……也没有……而是……（排除式"高级感"叙述）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'PHRASE', '仿佛在阐述一个既定事实（通用气氛贴纸，用于暗示角色平静或压迫）', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'PHRASE', '平静得像在说今天天气怎么样（被高频点名的语气模板）', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'PHRASE', '语气里带着……（每句台词后附语气标签，替代台词本身表现力）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'PHRASE', '声音里透着……（与"语气里带着"同类语气描写模板）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'PHRASE', '嗓音有些……（外在音色贴标签，而非通过台词内容传递潜台词）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'PHRASE', '语调变得……（对话后追加情绪注解，削弱对话本身的张力）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'PHRASE', '用一种……的口吻说（口吻标签替代对话行文）', 2, 1);

-- ─────────────────────────────────────────────
-- 类别 BODY_ACTION：身体动作链 / 器官轮班 (C 系列)
-- ─────────────────────────────────────────────
INSERT INTO `slop_patterns` VALUES
  (UNHEX(REPLACE(UUID(),'-','')), 'BODY_ACTION', '指尖轻敲桌面（沉默/思考通用填充动作）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'BODY_ACTION', '指节泛白 / 指尖泛白（紧张、愤怒、隐忍通用，过度复用）', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'BODY_ACTION', '死死咬住下唇（焦虑/克制通用动作模板）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'BODY_ACTION', '喉咙发紧（情绪紧张的低成本表达，过度复用）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'BODY_ACTION', '耳尖 / 耳根泛红（暧昧场景高频滥用）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'BODY_ACTION', '拿起咖啡或烟但不喝/不吸（"沉默/压抑"通用模板动作）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'BODY_ACTION', '嘴角勾起一个弧度（冷笑/玩味/压迫感通用模板）', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'BODY_ACTION', '眼中闪过某种光（器官轮班常见组件，情绪变化外包给眼神）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'BODY_ACTION', '瞳孔放大或收缩（过度细化的生理反应模板）', 2, 1);

-- ─────────────────────────────────────────────
-- 类别 IMAGERY：意象 / 比喻套件 (D 系列)
-- ─────────────────────────────────────────────
INSERT INTO `slop_patterns` VALUES
  (UNHEX(REPLACE(UUID(),'-','')), 'IMAGERY', '石子投入湖面 / 涟漪意象（多平台反复出现的 AI 八股意象）', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'IMAGERY', '影子被夕阳 / 月光 / 路灯拉得很长（场景收尾意象模板）', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'IMAGERY', '阳光透过窗户洒进来，在地上投下光斑（场景开场氛围模板）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'IMAGERY', '轻得像羽毛（情感轻柔的通用比喻）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'IMAGERY', '小兽般呜咽（亲密场景通用意象，滥用时失去感染力）', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'IMAGERY', '离水的鱼 / 煮熟的虾米（窘迫/慌乱状态通用比喻）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'IMAGERY', '手术刀般精准（能力/洞察力 AI 八股比喻）', 2, 1);

-- ─────────────────────────────────────────────
-- 类别 ENDING_CLICHE：升华 / 收尾套话 (E 系列)
-- ─────────────────────────────────────────────
INSERT INTO `slop_patterns` VALUES
  (UNHEX(REPLACE(UUID(),'-','')), 'ENDING_CLICHE', '这就够了（抽象升华收尾，无具体剧情钩子）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'ENDING_CLICHE', '夜还很长（抽象时间留白，替代具体未解决问题）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'ENDING_CLICHE', '不急，慢慢来（情绪抚慰型收尾套话）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'ENDING_CLICHE', '才刚刚开始 / 游戏才刚刚开始（预告式升华，无具体内容支撑）', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'ENDING_CLICHE', '此刻，无人知晓（旁白式神秘感，替代具体情节悬念）', 2, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'ENDING_CLICHE', '注定无法平静 / 无人能够预料（命运旁白腔，缺乏具体后果锚点）', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'ENDING_CLICHE', '传奇永不落幕（空洞升华，无任何情节依据）', 2, 1);

-- ─────────────────────────────────────────────
-- 类别 NARRATIVE_MECHANIC：叙事机制问题 (H 系列)
-- ─────────────────────────────────────────────
INSERT INTO `slop_patterns` VALUES
  (UNHEX(REPLACE(UUID(),'-','')), 'NARRATIVE_MECHANIC', '事件传送带写法：一个事件结束立刻开始下一个，无修整、后果、关系沉淀的间隙', 4, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'NARRATIVE_MECHANIC', '无效日常缓冲：连续闲聊/吃饭/逛街但不改变关系、不推进目标', 3, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'NARRATIVE_MECHANIC', '固定 NPC 主角：只有战力/等级成长，欲望、判断、关系、代价意识没有变化', 4, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'NARRATIVE_MECHANIC', '设定吸收失败：输入大量设定，正文只抓人名关键词，仍按通用模板写', 4, 1),
  (UNHEX(REPLACE(UUID(),'-','')), 'NARRATIVE_MECHANIC', '主角人设漂移：温和/闷骚/普通人设无故漂移成冷笑、霸道、冷酷爽文主角', 4, 1);

SET FOREIGN_KEY_CHECKS = 1;
