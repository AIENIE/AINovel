# V2 Style & Voice API
- 鉴权：Bearer Token（故事所有者）
- 基础路径：`/api/v2`

## Style Profiles
- `GET /stories/{storyId}/style-profiles`：风格画像列表。
- `POST /stories/{storyId}/style-profiles`：创建画像。
- `PUT /stories/{storyId}/style-profiles/{id}`：更新画像。
- `DELETE /stories/{storyId}/style-profiles/{id}`：删除画像。
- `POST /stories/{storyId}/style-profiles/{id}/activate`：激活画像（同故事仅一个 active）。

## Style Analysis
- `POST /style-analysis`：创建风格分析任务并返回分析结果。

## Character Voices
- `GET /stories/{storyId}/character-voices`：角色声音列表。
- `POST /stories/{storyId}/character-voices`：创建角色声音。
- `PUT /stories/{storyId}/character-voices/{id}`：更新角色声音。
- `DELETE /stories/{storyId}/character-voices/{id}`：删除角色声音。
- `POST /stories/{storyId}/character-voices/{id}/generate`：AI 生成角色声音草案。
