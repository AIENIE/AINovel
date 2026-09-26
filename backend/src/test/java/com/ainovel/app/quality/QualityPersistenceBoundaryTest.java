package com.ainovel.app.quality;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QualityPersistenceBoundaryTest {
    @Test void snapshotsAndAppliesInShortTransactionsRejectingConcurrentEdit() {
        var repo = mock(ManuscriptRepository.class); var tx = mock(TransactionTemplate.class); var active = new AtomicBoolean();
        when(tx.execute(any())).thenAnswer(i -> { active.set(true); try { return ((TransactionCallback<?>)i.getArgument(0)).doInTransaction(null); } finally { active.set(false); } });
        Manuscript manuscript = new Manuscript(); manuscript.setId(UUID.randomUUID()); manuscript.setVersion(1);
        when(repo.findWithStoryById(manuscript.getId())).thenReturn(Optional.of(manuscript));
        when(repo.findByIdForUpdate(manuscript.getId())).thenReturn(Optional.of(manuscript));
        var boundary = new QualityPersistenceBoundary(tx,repo);
        var snapshot = boundary.snapshot(manuscript,m -> { assertTrue(active.get()); return "prompt"; });
        assertFalse(active.get(),"remote generation must run after snapshot transaction closes");
        manuscript.setVersion(2); AtomicBoolean wrote = new AtomicBoolean();
        assertThrows(RuntimeException.class, () -> boundary.save(snapshot,m -> { wrote.set(true); return "result"; },value -> value));
        assertFalse(wrote.get()); assertFalse(active.get());
    }
}
