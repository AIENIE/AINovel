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
    public void requireEmpty(UUID worldId,UUID entryId,Long revision) {
        if(revision==null)throw WorldEntryStore.error("WORLD_REVISION_REQUIRED");
        if(!Objects.toString(input(worldId,entryId,revision).entry().content(),"").isBlank())throw WorldEntryStore.error("WORLD_ENTRY_NOT_EMPTY");
    }
    public WorldDetailDto generate(UUID worldId,UUID entryId,WorldGenerationCheckpoint checkpoint) {
        var frozen=input(worldId,entryId,null);checkpoint.validate(legacy.get(worldId));
        if(!Objects.toString(frozen.entry().content(),"").isBlank())throw WorldEntryStore.error("WORLD_ENTRY_NOT_EMPTY");
        String output=checkpoint.field("entry",entryId.toString(),()->{
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
        var response=ai.refine(frozen.user(),new AiRefineRequest(request.text(),
            (instruction.isBlank()?"请改善条目表述，保持原有事实。":instruction)+"\n世界与条目背景：\n"+store.write(frozen.content()),null)
            .forFeature(store.feature(frozen.entry(),"refine")));
        nonempty(response);return response;
    }
    private static String nonempty(AiRefineResponse response) {
        String text=response==null?"":Objects.toString(response.result(),"").strip();
        if(text.isBlank())throw WorldEntryStore.error("WORLD_AI_EMPTY_RESULT");return text;
    }
}
