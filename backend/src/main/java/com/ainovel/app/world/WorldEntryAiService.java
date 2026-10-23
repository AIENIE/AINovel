package com.ainovel.app.world;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.AiOperationExecutionContext;
import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.ainovel.app.world.dto.WorldDetailDto;
import com.ainovel.app.world.repo.WorldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;

/** Single-entry generation shares the durable world task checkpoint and existing billing path. */
@Service
public class WorldEntryAiService {
    private final WorldRepository worlds;
    private final WorldEntryStore store;
    private final WorldService legacy;
    private final ResourceAccessGuard access;
    private final AiService ai;
    private final TransactionTemplate tx;
    @org.springframework.beans.factory.annotation.Autowired private WorldPromptRenderer prompts;
    public WorldEntryAiService(WorldRepository worlds,WorldEntryStore store,WorldService legacy,ResourceAccessGuard access,AiService ai,TransactionTemplate tx) {
        this.worlds=worlds;this.store=store;this.legacy=legacy;this.access=access;this.ai=ai;this.tx=tx;
    }
    private record Input(User user,Content content,Entry entry) {}
    private Input input(UUID worldId,UUID entryId,Long revision) {
        return tx.execute(status->{
            var world=worlds.findById(worldId).orElseThrow(()->WorldEntryStore.error("WORLD_NOT_FOUND"));
            access.assertOwner(world.getUser());world.getUser().getUsername();
            if(revision!=null)WorldVersionService.checkRevision(world,revision);
            var content=store.content(world);
            return new Input(world.getUser(),content,content.entries().stream().filter(e->e.id().equals(entryId)).findFirst()
                .orElseThrow(()->WorldEntryStore.error("WORLD_ENTRY_NOT_FOUND")));
        });
    }
    public AiChatRequest freezeRequest(UUID worldId,UUID entryId,Long revision) {
        var input=input(worldId,entryId,revision);
        return prompts.entry(input.user(),input.content(),input.entry(),"补全该条目正文",false).forFeature(store.feature(input.entry(),"generate"));
    }
    public Map<String,AiChatRequest> freezeModules(UUID worldId,List<String> moduleKeys) {
        return tx.execute(status->{
            var world=worlds.findById(worldId).orElseThrow();access.assertOwner(world.getUser());var content=store.content(world);
            var projected=new TreeMap<String,Map<String,String>>();
            for(var module:legacy.definitions())if(moduleKeys.contains(module.key())) {
                var fields=new TreeMap<String,String>();for(var field:module.fields())fields.put(field.key(),content.modules().getOrDefault(module.key(),Map.of()).getOrDefault(field.key(),""));projected.put(module.key(),fields);
            }
            var requests=new LinkedHashMap<String,AiChatRequest>();
            for(var generated:store.legacyEntries(worldId,projected))if(Objects.toString(generated.content(),"").isBlank()) {
                var entry=content.entries().stream().filter(e->Objects.equals(e.legacyModuleKey(),generated.legacyModuleKey())&&Objects.equals(e.legacyFieldKey(),generated.legacyFieldKey())).findFirst().orElse(generated);
                requests.put(entry.legacyModuleKey()+":"+entry.legacyFieldKey(),prompts.entry(world.getUser(),content,entry,"补全该字段正文",false).forFeature(store.feature(entry,"generate")));
            }
            return Map.copyOf(requests);
        });
    }
    public void requireEmpty(UUID worldId,UUID entryId,Long revision) {
        if(revision==null)throw WorldEntryStore.error("WORLD_REVISION_REQUIRED");
        if(!Objects.toString(input(worldId,entryId,revision).entry().content(),"").isBlank())throw WorldEntryStore.error("WORLD_ENTRY_NOT_EMPTY");
    }
    public WorldDetailDto generate(UUID worldId,UUID entryId,WorldGenerationCheckpoint checkpoint) {
        var frozen=input(worldId,entryId,null);checkpoint.validate(legacy.get(worldId));
        if(!Objects.toString(frozen.entry().content(),"").isBlank())throw WorldEntryStore.error("WORLD_ENTRY_NOT_EMPTY");
        String output=checkpoint.field("entry",entryId.toString(),()->{
            var request=checkpoint.request("entry",entryId.toString());
            if(request!=null) {
                var response=ai.chat(frozen.user(),request);String result=response==null?"":Objects.toString(response.content(),"").strip();
                if(result.isBlank())throw WorldEntryStore.error("WORLD_AI_EMPTY_RESULT");return result;
            }
            var response=ai.refine(frozen.user(),new AiRefineRequest(store.write(frozen.content()),
                "请补全世界条目“"+frozen.entry().title()+"”的正文。只返回该条目的设定内容，与提供的已有设定一致，不改写其他条目。",null)
                .forFeature(store.feature(frozen.entry(),"generate")));
            return nonempty(response);
        });
        return AiOperationExecutionContext.complete(()->tx.execute(status->{
            var world=worlds.findByIdForUpdate(worldId).orElseThrow();access.assertOwner(world.getUser());
            checkpoint.validate(legacy.get(worldId));
            var entries=store.entries(world);
            var target=entries.stream().filter(e->e.id().equals(entryId)).findFirst().orElseThrow(()->WorldEntryStore.error("WORLD_ENTRY_NOT_FOUND"));
            if(!Objects.toString(target.content(),"").isBlank())throw WorldEntryStore.error("WORLD_ENTRY_NOT_EMPTY");
            var next=entries.stream().map(e->e.id().equals(entryId)?new Entry(e.id(),e.type(),e.title(),e.summary(),output,e.aliases(),e.tags(),e.legacyModuleKey(),e.legacyFieldKey()):e).toList();
            store.save(world,next,store.relations(worldId));worlds.saveAndFlush(world);
            var result=legacy.get(worldId);checkpoint.applied("entry:"+entryId,result);return result;
        }));
    }
    public AiRefineResponse refine(UUID worldId,UUID entryId,RefineRequest request) {
        var frozen=input(worldId,entryId,null);
        if(request.text()==null||request.text().isBlank())throw WorldEntryStore.error("WORLD_ENTRY_TEXT_REQUIRED");
        String instruction=Objects.toString(request.instruction(),"").strip();
        var e=frozen.entry();
        var original=new Entry(e.id(),e.type(),e.title(),e.summary(),request.text(),e.aliases(),e.tags(),e.legacyModuleKey(),e.legacyFieldKey());
        var response=ai.chat(frozen.user(),prompts.entry(frozen.user(),frozen.content(),original,instruction,true).forFeature(store.feature(e,"refine")));
        var result=new AiRefineResponse(response==null?"":response.content(),response==null?null:response.usage(),response==null?0:response.remainingCredits());
        nonempty(result);return result;
    }

    private static String nonempty(AiRefineResponse response) {
        String text=response==null?"":Objects.toString(response.result(),"").strip();
        if(text.isBlank())throw WorldEntryStore.error("WORLD_AI_EMPTY_RESULT");return text;
    }
}
