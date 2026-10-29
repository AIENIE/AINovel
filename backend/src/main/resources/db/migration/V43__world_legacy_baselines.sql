-- Preserve exact legacy bytes. No historical publications are inferred.
UPDATE worlds SET legacy_modules_json=modules_json;

INSERT INTO world_entries(id,world_id,type,title,summary,content,aliases_json,tags_json,legacy_module_key,legacy_field_key)
SELECT UNHEX(SUBSTRING(SHA2(CONCAT(HEX(w.id),':',m.module_key,':',f.field_key),256),1,32)),w.id,
    CASE
        WHEN m.module_key='geography' AND f.field_key IN ('terrain','climate') THEN 'GEOGRAPHY'
        WHEN m.module_key='geography' AND f.field_key='locations' THEN 'LOCATION'
        WHEN m.module_key='society' AND f.field_key IN ('politics','economy','culture') THEN 'SOCIETY'
        WHEN m.module_key='magic_tech' AND f.field_key='system_name' THEN 'SYSTEM'
        WHEN m.module_key='magic_tech' AND f.field_key IN ('rules','limitations') THEN 'RULE'
        ELSE 'CUSTOM' END,
    CASE CONCAT(m.module_key,'.',f.field_key)
        WHEN 'geography.terrain' THEN '地形地貌' WHEN 'geography.climate' THEN '气候特征'
        WHEN 'geography.locations' THEN '重要地点' WHEN 'society.politics' THEN '政治体制'
        WHEN 'society.economy' THEN '经济结构' WHEN 'society.culture' THEN '文化'
        WHEN 'magic_tech.system_name' THEN '体系名称' WHEN 'magic_tech.rules' THEN '规则'
        WHEN 'magic_tech.limitations' THEN '限制' ELSE LEFT(CONCAT(m.module_key,'.',f.field_key),255) END,
    '',
    CASE WHEN JSON_TYPE(JSON_EXTRACT(w.modules_json,CONCAT('$.',JSON_QUOTE(m.module_key),'.',JSON_QUOTE(f.field_key))))='NULL'
        THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(w.modules_json,CONCAT('$.',JSON_QUOTE(m.module_key),'.',JSON_QUOTE(f.field_key)))) END,
    '[]','[]',m.module_key,f.field_key
FROM worlds w
JOIN JSON_TABLE(CASE WHEN JSON_VALID(w.modules_json) THEN JSON_KEYS(w.modules_json) ELSE JSON_ARRAY() END,
    '$[*]' COLUMNS(module_key VARCHAR(255) PATH '$')) m
JOIN JSON_TABLE(JSON_KEYS(JSON_EXTRACT(w.modules_json,CONCAT('$.',JSON_QUOTE(m.module_key)))),
    '$[*]' COLUMNS(field_key VARCHAR(255) PATH '$')) f;

-- Non-object legacy values remain visible as free entries, as well as in the retained source.
INSERT INTO world_entries(id,world_id,type,title,summary,content,aliases_json,tags_json,legacy_module_key,legacy_field_key)
SELECT UNHEX(SUBSTRING(SHA2(CONCAT(HEX(w.id),':raw:',m.module_key),256),1,32)),w.id,'CUSTOM',LEFT(m.module_key,255),'',
    JSON_UNQUOTE(JSON_EXTRACT(w.modules_json,CONCAT('$.',JSON_QUOTE(m.module_key)))),'[]','[]',NULL,NULL
FROM worlds w
JOIN JSON_TABLE(CASE WHEN JSON_VALID(w.modules_json) THEN JSON_KEYS(w.modules_json) ELSE JSON_ARRAY() END,
    '$[*]' COLUMNS(module_key VARCHAR(255) PATH '$')) m
WHERE JSON_TYPE(JSON_EXTRACT(w.modules_json,CONCAT('$.',JSON_QUOTE(m.module_key))))<>'OBJECT';

INSERT INTO world_entries(id,world_id,type,title,summary,content,aliases_json,tags_json)
SELECT UNHEX(SUBSTRING(SHA2(CONCAT(HEX(id),':raw'),256),1,32)),id,'CUSTOM','历史原始设定','',modules_json,'[]','[]'
FROM worlds WHERE modules_json IS NOT NULL AND
    (NOT JSON_VALID(modules_json) OR (JSON_VALID(modules_json) AND JSON_TYPE(modules_json) NOT IN ('OBJECT','NULL')));

UPDATE worlds SET draft_content_hash=SHA2(COALESCE(modules_json,''),256);

INSERT INTO world_versions(id,world_id,sequence_number,origin,legacy_version,draft_revision,content_hash,snapshot_json,release_note,created_at)
SELECT UNHEX(SUBSTRING(SHA2(CONCAT(HEX(w.id),':baseline'),256),1,32)),w.id,0,'MIGRATION_BASELINE',w.version,w.revision,
    SHA2(CONCAT(COALESCE(w.modules_json,''),COALESCE(w.name,'')),256),
    JSON_OBJECT('name',w.name,'tagline',w.tagline,
        'themes',CASE WHEN JSON_VALID(w.themes_json) AND JSON_TYPE(w.themes_json)='ARRAY' THEN CAST(w.themes_json AS JSON) ELSE JSON_ARRAY() END,
        'creativeIntent',w.creative_intent,'notes',w.notes,
        'entries',COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('id',LOWER(BIN_TO_UUID(e.id)),'type',e.type,'title',e.title,
            'summary',e.summary,'content',e.content,'aliases',JSON_ARRAY(),'tags',JSON_ARRAY(),
            'legacyModuleKey',e.legacy_module_key,'legacyFieldKey',e.legacy_field_key)) FROM world_entries e WHERE e.world_id=w.id),JSON_ARRAY()),
        'relations',JSON_ARRAY(),'modules',JSON_OBJECT(),'legacyModulesJson',w.legacy_modules_json),
    '迁移时当前内容基线；不代表已恢复历史发布版本。',CURRENT_TIMESTAMP(6)
FROM worlds w WHERE w.status='active'
    OR EXISTS(SELECT 1 FROM stories s WHERE LOWER(REPLACE(s.world_id,'-',''))=LOWER(HEX(w.id)) AND s.user_id=w.user_id)
    OR EXISTS(SELECT 1 FROM outlines o JOIN stories s ON s.id=o.story_id WHERE LOWER(REPLACE(o.world_id,'-',''))=LOWER(HEX(w.id)) AND s.user_id=w.user_id)
    OR EXISTS(SELECT 1 FROM manuscripts m JOIN outlines o ON o.id=m.outline_id JOIN stories s ON s.id=o.story_id
        WHERE LOWER(REPLACE(m.world_id,'-',''))=LOWER(HEX(w.id)) AND s.user_id=w.user_id);

UPDATE stories s JOIN worlds w ON LOWER(REPLACE(s.world_id,'-',''))=LOWER(HEX(w.id)) AND s.user_id=w.user_id
    JOIN world_versions v ON v.world_id=w.id AND v.origin='MIGRATION_BASELINE' SET s.world_version_id=v.id;
UPDATE outlines o JOIN stories s ON s.id=o.story_id
    JOIN worlds w ON LOWER(REPLACE(o.world_id,'-',''))=LOWER(HEX(w.id)) AND s.user_id=w.user_id
    JOIN world_versions v ON v.world_id=w.id AND v.origin='MIGRATION_BASELINE' SET o.world_version_id=v.id;
UPDATE manuscripts m JOIN outlines o ON o.id=m.outline_id JOIN stories s ON s.id=o.story_id
    JOIN worlds w ON LOWER(REPLACE(m.world_id,'-',''))=LOWER(HEX(w.id)) AND s.user_id=w.user_id
    JOIN world_versions v ON v.world_id=w.id AND v.origin='MIGRATION_BASELINE' SET m.world_version_id=v.id;
