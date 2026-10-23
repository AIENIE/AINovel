package com.ainovel.app.modelconfig;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicitly persisted by both worker systems; never inherited by unrelated threads. */
public final class AiSelectionContext {
    public record Snapshot(String identity, Map<String,AiModelSelection> selections,String businessScope,Map<String,Integer> outputBudgets,
            com.ainovel.app.world.WorldBindingService.Frozen worldInput,com.ainovel.app.worldassistant.WorldAssistantService.Frozen worldAssistantInput) {
        public Snapshot(String identity,Map<String,AiModelSelection> selections,String scope,Map<String,Integer> budgets,com.ainovel.app.world.WorldBindingService.Frozen worldInput){this(identity,selections,scope,budgets,worldInput,null);}
        public Snapshot(String identity,Map<String,AiModelSelection> selections,String scope,Map<String,Integer> budgets){this(identity,selections,scope,budgets,null);}
        public Snapshot(String identity,Map<String,AiModelSelection> selections){this(identity,selections,null,Map.of());}
        public Snapshot(String identity,Map<String,AiModelSelection> selections,String scope){this(identity,selections,scope,Map.of());}
        public Snapshot { selections=Map.copyOf(selections);outputBudgets=outputBudgets==null?Map.of():Map.copyOf(outputBudgets); }
        public int outputBudget(String feature) {return outputBudgets.getOrDefault(feature,4096);}
    }
    private record Scope(Snapshot snapshot, Map<String,AtomicInteger> calls) {}
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private AiSelectionContext() {}
    public static Snapshot current() { return CURRENT.get()==null?null:CURRENT.get().snapshot(); }
    public static AiModelSelection selection(String feature) {
        Snapshot s=current();
        if(s==null) return null;
        AiModelSelection selected=s.selections().get(feature);
        if(selected==null) throw new com.ainovel.app.common.ApiStatusException(
                org.springframework.http.HttpStatus.CONFLICT,"AI_STEP_NOT_QUOTED");
        return selected;
    }
    public static String callKey(String hash) {
        Scope s=CURRENT.get();
        if(s==null) return null;
        int ordinal=s.calls().computeIfAbsent(hash,k->new AtomicInteger()).getAndIncrement();
        UUID operation=com.ainovel.app.aioperation.AiOperationExecutionContext.operationId();
        return (operation==null?s.snapshot().identity():operation.toString())+":"+AiActionPlanner.hash(hash).substring(0,24)+":"+ordinal;
    }
    public interface ScopeHandle extends AutoCloseable { @Override void close(); }
    public static ScopeHandle install(Snapshot snapshot) {
        Scope prior=CURRENT.get();
        if(snapshot==null) CURRENT.remove(); else CURRENT.set(new Scope(snapshot,new HashMap<>()));
        return ()->{if(prior==null) CURRENT.remove();else CURRENT.set(prior);};
    }
}
