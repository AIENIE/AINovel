package com.ainovel.app.worldcontext;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.world.WorldBindingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;
import static com.ainovel.app.worldcontext.WorldContextDtos.*;

@Service
public class WorldContextPreferences {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ResourceAccessGuard access;
    private final OutlineRepository outlines;
    private final WorldBindingService bindings;
    private final WorldContextSelector selector;
    public WorldContextPreferences(JdbcTemplate jdbc,ObjectMapper json,ResourceAccessGuard access,OutlineRepository outlines,
            WorldBindingService bindings,WorldContextSelector selector) {
        this.jdbc=jdbc;this.json=json;this.access=access;this.outlines=outlines;this.bindings=bindings;this.selector=selector;
    }
    public static String scope(Outline outline){return "outline:"+outline.getId();}
    public static String scope(Manuscript m,UUID scene){return "manuscript:"+m.getId()+":scene:"+scene;}
    public Outline outline(User user,UUID id) {
        var o=outlines.findByIdWithStoryUser(id).orElseThrow(()->new ApiStatusException(HttpStatus.NOT_FOUND,"OUTLINE_NOT_FOUND"));
        access.requireOwnedStory(o.getStory().getId(),user);return o;
    }
    public Manuscript manuscript(User user,UUID id,UUID scene) {
        var m=access.requireOwnedManuscript(id,user);
        if(!containsScene(m.getOutline(),scene))throw WorldContextSelector.error("WORLD_CONTEXT_SCENE_MISSING");return m;
    }
    private boolean containsScene(Outline outline,UUID scene) {
        try{for(var chapter:json.readTree(outline.getContentJson()).path("chapters"))for(var s:chapter.path("scenes"))if(scene.toString().equals(s.path("id").asText()))return true;return false;}
        catch(Exception e){throw new IllegalStateException("OUTLINE_CONTENT_INVALID",e);}
    }
    public Preferences read(String scope,WorldBindingService.Resolved binding) {
        if(binding==null)return Preferences.defaults(null);
        bindings.requireUsable(binding);
        boolean writing=org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()&&!org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        var rows=jdbc.query("select * from world_context_preferences where scope_key=? and world_id=?"+(writing?" for update":""),(r,n)->new Preferences(binding.worldId(),r.getLong("revision"),uuid(r.getBytes("reviewed_version_id")),
                parse(r.getString("choices_json"),new TypeReference<Map<String,Choice>>(){}),parse(r.getString("reviewed_hashes_json"),new TypeReference<Map<String,String>>(){})),scope,bytes(binding.worldId()));
        return rows.isEmpty()?Preferences.defaults(binding.worldId()):rows.getFirst();
    }
    @Transactional(readOnly=true)
    public Preferences get(User user,UUID outlineId,UUID manuscriptId,UUID sceneId) {
        if(manuscriptId!=null){var m=manuscript(user,manuscriptId,sceneId);return read(scope(m,sceneId),bindings.resolve(m));}
        var o=outline(user,outlineId);return read(scope(o),bindings.resolve(o.getStory(),o,null));
    }
    @Transactional
    public Preferences save(User user,UUID outlineId,UUID manuscriptId,UUID sceneId,Save request) {
        String scope;WorldBindingService.Resolved binding;
        if(manuscriptId!=null){var m=manuscript(user,manuscriptId,sceneId);scope=scope(m,sceneId);binding=bindings.lockCurrent(m);}
        else {var o=outline(user,outlineId);scope=scope(o);binding=bindings.lockCurrent(o);}
        bindings.requireUsable(binding);
        if(binding==null||request==null||!Objects.equals(binding.worldId(),request.worldId())||!Objects.equals(binding.versionId(),request.versionId()))throw conflict("WORLD_CONTEXT_BINDING_CHANGED");
        var current=read(scope,binding);
        if(request.expectedRevision()==null||request.expectedRevision()!=current.revision())throw conflict("WORLD_CONTEXT_SELECTION_CHANGED");
        if(request.choices()==null||request.choices().size()>2500)throw WorldContextSelector.error("WORLD_CONTEXT_CHOICES_INVALID");
        var hashes=selector.sourceHashes(binding);var choices=new TreeMap<String,Choice>();
        for(var e:request.choices().entrySet()) {
            if(e.getKey()==null||e.getValue()==null)throw WorldContextSelector.error("WORLD_CONTEXT_CHOICES_INVALID");
            if(e.getValue()==Choice.AUTO)continue;
            if(!hashes.containsKey(e.getKey())&&!current.choices().containsKey(e.getKey()))throw WorldContextSelector.error("WORLD_CONTEXT_SOURCE_MISSING");
            choices.put(e.getKey(),e.getValue());
        }
        var next=new Preferences(binding.worldId(),current.revision()+1,binding.versionId(),choices,hashes);
        if(current.revision()==0)jdbc.update("insert into world_context_preferences(scope_key,world_id,user_id,revision,reviewed_version_id,choices_json,reviewed_hashes_json) values(?,?,?,?,?,?,?)",
                scope,bytes(binding.worldId()),bytes(user.getId()),next.revision(),bytes(binding.versionId()),write(choices),write(hashes));
        else jdbc.update("update world_context_preferences set revision=?,reviewed_version_id=?,choices_json=?,reviewed_hashes_json=?,updated_at=CURRENT_TIMESTAMP(6) where scope_key=? and world_id=?",
                next.revision(),bytes(binding.versionId()),write(choices),write(hashes),scope,bytes(binding.worldId()));
        return next;
    }
    public static void check(Selection selection,String expectedHash,Long expectedRevision) {
        WorldContextSelector.requireUsable(selection);
        if(expectedHash!=null&&!expectedHash.equals(selection.contextHash()))throw conflict("WORLD_CONTEXT_CHANGED");
        if(expectedRevision!=null&&expectedRevision!=selection.selectionRevision())throw conflict("WORLD_CONTEXT_SELECTION_CHANGED");
    }
    public static ApiStatusException conflict(String code){return new ApiStatusException(HttpStatus.CONFLICT,code);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private <T>T parse(String text,TypeReference<T> type){try{return json.readValue(text,type);}catch(Exception e){throw new IllegalStateException("WORLD_CONTEXT_STORED_DATA_INVALID",e);}}
}
