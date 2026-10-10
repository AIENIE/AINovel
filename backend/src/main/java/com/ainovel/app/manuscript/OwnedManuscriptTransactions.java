package com.ainovel.app.manuscript;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.function.Function;

/** Resolve ownership and lazy content in the same transaction as the business operation. */
@Service
public class OwnedManuscriptTransactions {
    private final ResourceAccessGuard access;
    public OwnedManuscriptTransactions(ResourceAccessGuard access) { this.access = access; }

    @Transactional
    public <T> T write(UUID id, User user, Function<Manuscript, T> operation) {
        return operation.apply(access.requireOwnedManuscript(id, user));
    }

    @Transactional(readOnly = true)
    public <T> T read(UUID id, User user, Function<Manuscript, T> operation) {
        return operation.apply(access.requireOwnedManuscript(id, user));
    }
}
