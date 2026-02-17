# V2 Version Control API
- 鉴权：Bearer Token（稿件所有者）
- 基础路径：`/api/v2`

## Versions
- `GET /manuscripts/{manuscriptId}/versions`：版本列表（首次会自动初始化 `main` 分支 + 初始快照）。
- `POST /manuscripts/{manuscriptId}/versions`：创建快照（支持 `label/snapshotType/branchId`）。
- `GET /manuscripts/{manuscriptId}/versions/{versionId}`：版本详情。
- `POST /manuscripts/{manuscriptId}/versions/{versionId}/rollback`：回滚稿件到目标快照。
- `GET /manuscripts/{manuscriptId}/versions/diff?fromVersionId=&toVersionId=`：差异统计。

## Branches
- `GET /manuscripts/{manuscriptId}/branches`：分支列表。
- `POST /manuscripts/{manuscriptId}/branches`：创建分支。
- `PUT /manuscripts/{manuscriptId}/branches/{branchId}`：更新分支元信息。
- `POST /manuscripts/{manuscriptId}/branches/{branchId}/merge`：合并分支（创建 merge 快照）。
- `DELETE /manuscripts/{manuscriptId}/branches/{branchId}`：废弃分支。

## Auto Save
- `GET /users/me/auto-save-config`：读取自动保存配置。
- `PUT /users/me/auto-save-config`：更新自动保存配置（`autoSaveIntervalSeconds >= 30`, `maxAutoVersions >= 10`）。
