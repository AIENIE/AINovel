# 第一批后端整改：导出、分支与关键存储完整性

日期：2026-09-27。对应审计项：MAINT-01、MAINT-02、MAINT-03，以及版本差异缓存的作品归属检查。仅本地实现与隔离验证；无部署、真实业务数据库写入或付费 AI。

## 已实施

- 将 `V2ExportRenderer` 的格式输出拆为 `TxtExportRenderer`、`DocxExportRenderer`、`EpubExportRenderer`、`PdfExportRenderer`，共用规范化 `ExportDocument`。PDF 使用 PDFBox 3.0.8、A4 边距、按字形宽度换行与自动分页，移除中文替换问号和手工 PDF 对象拼接。
- 内置静态 `AINovelSansSC-Regular.ttf`，由 Google Fonts 固定提交的 Noto Sans SC 2.004 在 weight=400 生成。OFL 1.1 许可、源提交、源/产物 SHA-256 和重建步骤均在 `backend/src/main/resources/fonts/`；运行时校验产物哈希，不读取主机字体、不联网取字体。字体为 10,595,960 字节，PDF 子集嵌入本次用到的字形。
- Unicode 码点逐个处理，不拆代理对。遇到字体未覆盖字符时返回 `PDF_UNSUPPORTED_CHARACTER_U+...`，不以问号或方框代替。支持字形范围以内的补充平面字经过了完整回读；这不是“支持所有 Unicode 字形”的承诺。
- 为创建、更新、合并分支引入独立 DTO 与 enum。明确拒绝未知字段、错误类型、显式 null、未知合并策略/逐场选择值、非法 UUID；`{}` 合并仍明确默认 REPLACE_ALL。创建分支要求非空名称；合并请求体要求 JSON 对象，JSON null/空 HTTP body 不视为默认操作。
- 更新分支检查重名与状态转换：通用更新不能假冒合并完成或把已结束分支重新激活。合并前在同一事务保存当前工作分支快照；若当前在其他分支，还保存主线目标快照。主线当前已保存但尚未打检查点的编辑会参与逐场冲突判断，合并返回 `backupVersionId` 供追溯。
- `JsonColumnCodec` 新增 `readRequired`、`writeRequired`、`readSections`。关键正文拒绝缺值、坏 JSON、额外尾随 token、非对象或非字符串场景值；失败不附带可能含正文的解析器原因。保留已有可选配置的宽容 API，但稿件/版本关键路径已切换严格 API。`V2Json` 对非空损坏数据也不再返回成功空值。
- `V2VersionPersistenceService.diff` 在读取全局版本 ID 缓存前核验两个版本均属于当前授权稿件，关闭命中缓存后绕过作品范围的问题。

`ManuscriptService` 的严格 JSON 接入、通用请求反序列化 400 和 PDFBox 依赖已合并到本次整改代码。

## 本批测试与证据

已增加有业务结果的测试：

| 测试 | 验证内容 |
|---|---|
| `V2BranchRequestContractTest` | 经 HTTP/Jackson 拒绝未知策略、null、错误结构和非法字段，失败时服务未调用；合法空对象使用明确默认策略 |
| `V2BranchIntegrityTest` | 真实隔离 H2 事务中备份保留未检查点主线；冲突不写入；模拟序列化失败回滚已 flush 快照；坏源版本不覆盖；非法状态/重名拒绝；跨稿件缓存差异拒绝 |
| `JsonColumnCodecTest` 新增用例 | 关键 JSON 错误形状/损坏/尾随 token 拒绝，序列化失败无空对象替代和正文异常泄露 |
| `PdfExportRendererTest` | 内嵌字体哈希、中文及补充平面字回读、分页、首页/中页/末页渲染、缺字失败且无 PDF 字节输出 |
| 既有版本/持久化/叙事测试 | 迁移为强类型调用，继续核对合法业务路径 |

相关 Maven 测试与最终 L2 数量见 [统一验收记录](validation.md)。

已经独立完成 PDF 离线探针，不使用 Maven target：

- 以单独临时 classes 目录编译当前 `PdfExportRenderer`，使用 PDFBox-app 3.0.8 及仓库内字体生成 [四页中文样稿](evidence/pdf/chinese-pagination.pdf)。样稿全为构造测试内容。
- PDFBox 回读与独立 pypdf 回读均逐字比较规范化文本通过；包含中英文、标点和 U+20087 补充平面汉字。PDF 共 4 页，所有输出字体内嵌，正文没有截断或问号替换。
- 缺字样本 U+1F600 返回明确失败，输出字节数为 0。
- 使用独立 Poppler 90 DPI 渲染并逐页查看 [首页](evidence/pdf/poppler-page-1.png)、[中间页](evidence/pdf/poppler-page-3.png)、[末页](evidence/pdf/poppler-page-4.png)，未发现重叠、裁切、黑方块或缺尾文。
- [探针源代码](evidence/pdf/PdfRemediationProbe.java)随证据保留。PDF SHA-256：`abcbcc52308c401912059629d0f8dae0a684b3d522575c328940c24f983da466`。

## 兼容性与后续验收

本批的导出与分支修改没有更改历史 H1/H2 正文抽取或偏移规则；全方案的 V19–V23 新迁移见后续批次。TXT 的原段落与行尾规则仍由既有测试约束，DOCX/EPUB 只作格式职责拆分，本批不宣称完成全部阅读器兼容性验证。PDF 当前使用单一固定字体；遇到未覆盖的生僻字、emoji 或复杂文字应明确失败并提示选择其他格式，后续扩字体需重新检查许可、覆盖率和包大小。

分支 API 现在对明确的坏请求返回 4xx；依赖过去宽松 Map 行为的外部调用者需要遵循字段契约。当前前端发送合法策略和非空分支名，合并使用 `{}` 或显式对象，属于兼容路径。PDF 探针与 L2 分别验证格式语义和整体构建。
