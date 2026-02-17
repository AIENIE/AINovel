# V2 Workspace API
- 鉴权：Bearer Token（当前用户）
- 基础路径：`/api/v2`

## Workspace Layouts
- `GET /users/me/workspace-layouts`：布局列表。
- `POST /users/me/workspace-layouts`：创建布局。
- `PUT /users/me/workspace-layouts/{id}`：更新布局。
- `DELETE /users/me/workspace-layouts/{id}`：删除布局。
- `POST /users/me/workspace-layouts/{id}/activate`：激活布局。

## Writing Sessions
- `POST /writing-sessions/start`：开始会话。
- `PUT /writing-sessions/{id}/heartbeat`：会话心跳。
- `POST /writing-sessions/{id}/end`：结束会话。
- `GET /writing-sessions/stats`：会话统计。

## Writing Goals
- `GET /users/me/writing-goals`：目标列表。
- `POST /users/me/writing-goals`：创建目标。
- `PUT /users/me/writing-goals/{id}`：更新目标。
- `DELETE /users/me/writing-goals/{id}`：删除目标。

## Keyboard Shortcuts
- `GET /users/me/shortcuts`：快捷键配置。
- `PUT /users/me/shortcuts`：批量更新快捷键。
