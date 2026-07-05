package com.ainovel.app.story;

import com.ainovel.app.story.dto.StoryCreateRequest;
import org.springframework.stereotype.Service;

@Service
public class StoryConceptionPromptFactory {
    public PromptContext build(StoryCreateRequest request) {
        String hintedPromise = hintText(request, "corePromise");
        String hintedTruth = hintText(request, "hiddenTruth");
        String hintedMeme = hintText(request, "memeReference");
        String prompt = """
                你是长篇小说剧情策划编辑。请根据用户输入生成“结构骨架 + 双轨反转 + 大纲建议”，必须返回 JSON（不要 markdown），结构：
                {
                  "title":"故事标题",
                  "synopsis":"120-200字故事梗概",
                  "genre":"类型",
                  "tone":"风格基调",
                  "skeleton":{
                    "corePromise":"这本书最吸引读者继续读下去的承诺",
                    "centralQuestion":"主问题",
                    "hiddenTruth":"真正隐藏的真相",
                    "readerExpectation":"读者会先入为主相信什么"
                  },
                  "characters":[
                    {"name":"角色名","synopsis":"一句话定位","details":"关键背景","relationships":"与其他角色关系"}
                  ],
                  "twistOptions":[
                    {
                      "id":"twist-a",
                      "label":"保留灵感版",
                      "summary":"反转方案说明",
                      "setupPoints":["前置铺垫1","前置铺垫2"],
                      "misdirectionPoints":["误导点1","误导点2"],
                      "revealBeat":"揭示瞬间",
                      "revealTiming":"第几章或后段",
                      "earlyRevealRisk":"为什么可能被提前看穿",
                      "payoff":"回收后的惊喜点"
                    },
                    {
                      "id":"twist-b",
                      "label":"结构更强版",
                      "summary":"另一条更稳的反转方案",
                      "setupPoints":["前置铺垫1","前置铺垫2"],
                      "misdirectionPoints":["误导点1","误导点2"],
                      "revealBeat":"揭示瞬间",
                      "revealTiming":"第几章或后段",
                      "earlyRevealRisk":"为什么可能被提前看穿",
                      "payoff":"回收后的惊喜点"
                    }
                  ],
                  "foreshadowSeeds":[
                    {
                      "entryKey":"seed-1",
                      "name":"伏笔名称",
                      "setup":"埋点内容",
                      "coverLayer":"表面掩护层",
                      "misdirectionLayer":"额外误导层",
                      "payoff":"后续回收点"
                    }
                  ],
                  "memeStrategy":{
                    "sourceDomain":"梗来源领域",
                    "useCase":"使用目的",
                    "naturalVersion":"自然融合版",
                    "conservativeVersion":"保守版",
                    "immersionRisk":"可能的出戏风险"
                  },
                  "outlineSuggestion":{
                    "title":"策划大纲标题",
                    "chapters":[
                      {
                        "title":"章节标题",
                        "summary":"章节摘要",
                        "planning":{"purpose":"章节作用","informationRelease":"本章释放什么信息","twistRole":"setup|misdirect|reveal|payoff"},
                        "scenes":[
                          {
                            "title":"场景标题",
                            "summary":"场景摘要",
                            "planning":{
                              "foreshadowHint":"本场景埋什么",
                              "misdirectionAction":"如何误导",
                              "revealTrigger":"触发揭示的条件",
                              "payoffPlan":"未来如何回收"
                            }
                          }
                        ]
                      }
                    ]
                  },
                  "lorebookSeeds":[
                    {"entryKey":"seed-1","displayName":"条目名","category":"concept","content":"内容","keywords":["关键词"]}
                  ],
                  "graphSeeds":[
                    {"sourceKey":"seed-1","targetKey":"truth","relationType":"foreshadows","label":"指向隐藏真相"}
                  ]
                }
                用户输入：
                标题：%s
                梗概：%s
                类型：%s
                基调：%s
                核心承诺提示：%s
                隐藏真相提示：%s
                梗参考提示：%s
                """.formatted(
                safe(request.title()),
                safe(request.synopsis()),
                safe(request.genre()),
                safe(request.tone()),
                safe(hintedPromise),
                safe(hintedTruth),
                safe(hintedMeme)
        );
        return new PromptContext(prompt, hintedPromise, hintedTruth, hintedMeme);
    }

    private String hintText(StoryCreateRequest request, String key) {
        if (request == null || request.plotPlanningHints() == null) {
            return "";
        }
        Object value = request.plotPlanningHints().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record PromptContext(
            String prompt,
            String hintedPromise,
            String hintedTruth,
            String hintedMeme
    ) {
    }
}
