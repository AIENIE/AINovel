# 逐例代理阅读结果

先读记录见 agent-reviews.json；模型结果另列，不覆盖代理原判。G 的 same/uncertain 表示相对目标的定性观察，缺失对照时不代表胜负。E 均与同一原稿比较。

| 单元/臂 | 硬约束 | 机械/声音/流畅/多样性 | 阅读判断 |
|---|---|---|---|
| G1-base | fail | uncertain / same / worse / uncertain | 新增登记时间手动改不了这一关键证据、寄信人名字就在联上；结尾重复核对编号且远超目标长度。场景未揭姓名，但强行加入可确定性证据改变悬疑条件。 汉字数 1045，长度达标：False。 |
| G1-positive | pass | uncertain / same / same / uncertain | 保留停用不等于未投递、冻结时间和调表目标；结冰、落笔、落灰三处泛化意象。十之八九打印错误为人物推测而非叙述确证，记录为无依据倾向但不判硬失败。 汉字数 545，长度达标：False。 |
| G1-profile | fail | same / same / same / uncertain | 新增不是原件、条码后三位不匹配、没有流水、旧号手写可收件等关键事实；把纸张细节当谜题证据，超出只有两项记录的边界。 汉字数 633，长度达标：True。 |
| G2-base | fail | uncertain / uncertain / same / uncertain | 新增配电间跳闸、七层楼梯、事故报告与半月回避的共同经历，为关系和开门决策提供输入外因果事实。身份仍未知、未开门合格，但新增关键经历不合格。 汉字数 655，长度达标：True。 |
| G2-positive | fail | uncertain / uncertain / worse / uncertain | 末段新增一短回应，违反门内只回应三短两长；叙述断言水压不会敲特定节奏，削弱未知机制。身份未揭示；同意电话并守门保留。篇幅和动作尾注偏多。 汉字数 864，长度达标：False。 |
| G2-profile | fail | same / uncertain / same / uncertain | 新增管沟连主楼、水反上来须通知等决定风险判断的设施事实；老周身份为附带新设定。未开门、未确证回应者合格，但具体描写越界。 汉字数 679，长度达标：True。 |
| G3-positive | pass | same / same / same / uncertain | 工具盒只有扳手两垫圈、周日在家却回避、不揭示给父亲、最终仍桌上全部保留。绿漆胶布萝卜干属普通生活细节，未添加创伤病情或关键经历；开头餐桌又挪桌上指代含混，擦洗动作偏长。无其他臂，不能声称相对胜出。 汉字数 543，长度达标：False。 |
| G4-positive | pass | same / same / same / uncertain | 油锅清洁争执、删除道歉、消息只回咸豆花、提前洗锅且未宣告和好保留。香菜偏好和当面水流提醒为普通补充，不升级关系。豆浆卖完却庆幸开口早与豆花交易衔接不佳，结尾滴水停顿惯用；不能证明去禁用清单改善。 汉字数 636，长度达标：True。 |
| G5-positive | fail | same / uncertain / same / uncertain | 没有敲第三次，身份仍未明，但新增对方知道钟规、放药离去、开门取药等关键事件，使原定只决定第二次求查验推进成收药。夜值人是唯一查验方法也属新增设定。裂纹/毛刺为普通细节允许。与G2改变冻结回应的具体失败类不同，记录而不事后合并类目触发停止。 汉字数 667，长度达标：True。 |
| G6-positive | uncertain | same / same / same / uncertain | 三币/标价四币、一币加一夜、油仍对方手上、不凭蜡判真假均保留。新增膝盖旧伤，是否属于需批准的重要经历存在争议，保留uncertain而不把全部感官细节判失败。结尾超过要求看蜡一步至转瓶但未交付，风险较低。篇幅偏短；无完整三臂比较。 汉字数 431，长度达标：False。 |
| E1-minimal | pass | same / same / same / same | 空补丁合理，事实和人物口气原样保留；可选反馈不强制执行。 |
| E1-paragraph | pass | same / same / improved / same | 两处措辞微调，未真正段落重组；收益小而清楚，五项冻结问题全保留。无事实或声音删除。 |
| E2-minimal | pass | same / same / improved / same | 四个冻结问题均保留；保留两次推架的验证作用及今天不去的限定，不拉也在只提请作者确认。收益为局部语法整理，非强slop问题解决。 |
| E2-paragraph | pass | same / same / improved / same | 只做两处语法微调，尚未真正重组；不删停顿、回照及意象。四项硬约束通过。 |
| E3-minimal | pass | same / same / improved / same | 四项事实保留，光标停顿及票角照应不动。消歧清楚，无内容删除。 |
| E3-paragraph | pass | same / same / uncertain / same | 无事实或意图变化，但替换收益不确定，可能只是偏好；不应作为必改问题。反馈不把票位置原稿既有问题代修。 |
| E4-minimal | pass | improved / worse / improved / same | 五项硬事实和应答顺序保留；人工模板感下降，但情绪强度/服从口气变薄。不能把软损失算硬失败，也不应说完全无损。适合作者选择。 |
| E4-paragraph | pass | uncertain / same / uncertain / same | 只换同构解释位置，仍有两处语气里带着；表面变化不等于机械信息冗余消失。硬约束保留，明显表达收益不足。 |
| E5-minimal | fail | same / same / uncertain / same | 四个问题仍可答是，却没有覆盖这一变化；与E2同源时明确不改该字的行为不一致。三连拿放未处理。按本实验政策判硬失败，并非已证明作者本来想表达拉或找。 |
| E5-paragraph | pass | same / same / same / same | 不改避免修补原稿逻辑；反馈识别受控追加木尺拿放与已用作支撑的位置冲突，有实用价值，但对机械重复没有改善。样本扰动混入位置问题，不能视为纯句式对照。 |
| E6-minimal | pass | improved / same / improved / same | 四道事实保留，无关键删除；机械领起减少，仍留无前文支撑的抽象升华，目标问题只部分改善。不能称抽象总结已解决。 |
| E6-paragraph | pass | improved / same / improved / same | 两处局部整理有小收益，几乎不重组叙事；抽象结尾仍然泛化。仪式感降低是软节奏变化，无事实/人物意图损失。 |

未执行：G3–G6 的 base/profile，因 G1/G2 同类关键内容破坏触发预先停止规则。缺失不填为失败样本，也不填为成功样本。

每处编辑的原句、替换、理由与UTF-16位置在 experiment-candidates.json；每份全文和完整diff保留。
