# V2 Style & Voice API
- 鉴权：Bearer Token（故事所有者）
- 基础路径：`/api/v2`

## Style Profiles
- `GET /stories/{storyId}/style-profiles`：风格画像列表。
- `POST /stories/{storyId}/style-profiles`：创建画像。
- `PUT /stories/{storyId}/style-profiles/{id}`：更新画像。
- `DELETE /stories/{storyId}/style-profiles/{id}`：删除画像。
- `POST /stories/{storyId}/style-profiles/{id}/activate`：激活画像（同故事仅一个 active）。

风格画像持久化到 `style_profiles`，场景覆盖持久化到 `style_profile_scene_overrides`。请求和返回继续使用前端现有 camelCase 字段：
- `name`、`profileType`、`dimensions`、`sampleText`、`aiAnalysis`、`sceneOverrides`、`isActive`。
- `sceneOverrides` 每项保留 `sceneType` 和 `dimensions` 等原始覆盖内容。

## Style Analysis
- `POST /style-analysis`：创建风格分析任务并返回分析结果。

风格分析任务持久化到 `style_analysis_jobs`，返回 `status=completed` 和 `result`。当前分析仍是轻量本地启发式结果，不调用额外模型。

## Character Voices
- `GET /stories/{storyId}/character-voices`：角色声音列表。
- `POST /stories/{storyId}/character-voices`：创建角色声音。
- `PUT /stories/{storyId}/character-voices/{id}`：更新角色声音。
- `DELETE /stories/{storyId}/character-voices/{id}`：删除角色声音。
- `POST /stories/{storyId}/character-voices/{id}/generate`：AI 生成角色声音草案。

角色声音持久化到 `character_voices`。`characterCardId` 必须属于当前故事；同一角色只能存在一份声音设定，重复创建会返回冲突。
