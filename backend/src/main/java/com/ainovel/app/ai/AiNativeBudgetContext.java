package com.ainovel.app.ai;

import fireflychat.ai.v1.ChatCompletionsRequest;
import fireflychat.ai.v1.ChatCompletionsResponse;
import fireflychat.ai.v1.PrepareCallBudgetResponse;
import java.util.Objects;
import java.util.function.Function;

/** Server-owned run budget. Never populated from a public chat request or a model tool argument. */
public final class AiNativeBudgetContext {
    private static final ThreadLocal<Owner> CURRENT=new ThreadLocal<>();
    private AiNativeBudgetContext() {}
    public interface Owner {
        ChatCompletionsResponse call(ChatCompletionsRequest request,AiValidationMoneyGateway.Transport transport,
                                     Function<PrepareCallBudgetResponse,ChatCompletionsResponse> validationCall);
    }
    public static Owner current() {return CURRENT.get();}
    public static Scope open(Owner owner) {
        Owner previous=CURRENT.get();CURRENT.set(Objects.requireNonNull(owner));return new Scope(previous);
    }
    public static final class Scope implements AutoCloseable {
        private final Owner previous;private boolean closed;
        private Scope(Owner previous) {this.previous=previous;}
        @Override public void close() {
            if(closed)return;closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);
        }
    }
}
