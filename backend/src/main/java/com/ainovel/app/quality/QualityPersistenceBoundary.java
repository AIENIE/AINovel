package com.ainovel.app.quality;

import com.ainovel.app.aioperation.AiOperationExecutionContext;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.common.ApiStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class QualityPersistenceBoundary {
    private final TransactionTemplate transactions;
    private final ManuscriptRepository manuscripts;
    public QualityPersistenceBoundary(TransactionTemplate transactions, ManuscriptRepository manuscripts) {
        this.transactions = transactions; this.manuscripts = manuscripts;
    }
    public <T> Snapshot<T> snapshot(Manuscript manuscript, Function<Manuscript,T> reader) {
        return transactions.execute(status -> {
            Manuscript current = manuscripts.findWithStoryById(manuscript.getId()).orElseThrow();
            return new Snapshot<>(current.getId(), current.getVersion(), reader.apply(current));
        });
    }
    public <T,R> R save(Snapshot<T> snapshot, Function<Manuscript,R> writer, Function<R,Object> resultView) {
        Supplier<R> mutation = () -> transactions.execute(status -> {
            Manuscript current = manuscripts.findByIdForUpdate(snapshot.manuscriptId()).orElseThrow();
            if (current.getVersion() != snapshot.version()) throw new ApiStatusException(HttpStatus.CONFLICT, "MANUSCRIPT_VERSION_CONFLICT");
            return writer.apply(current);
        });
        return AiOperationExecutionContext.complete(mutation, resultView);
    }
    public record Snapshot<T>(UUID manuscriptId, long version, T data) { }
}
