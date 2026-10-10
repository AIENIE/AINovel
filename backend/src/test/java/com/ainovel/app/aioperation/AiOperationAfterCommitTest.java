package com.ainovel.app.aioperation;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiOperationAfterCommitTest {
    @Test void outerSaveDoesNotDispatchUntilCommitAndRollbackHasNoExternalWork() {
        var repository=mock(AiOperationRepository.class);var transactions=mock(TransactionTemplate.class);var handler=mock(AiOperationHandler.class);
        when(handler.type()).thenReturn("LANGUAGE_DIAGNOSIS");
        when(transactions.execute(any())).thenAnswer(i->((org.springframework.transaction.support.TransactionCallback<?>)i.getArgument(0)).doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
        when(repository.saveAndFlush(any())).thenAnswer(i->{AiOperationRun run=i.getArgument(0);org.springframework.test.util.ReflectionTestUtils.setField(run,"id",UUID.randomUUID());return run;});
        var scheduled=new ArrayList<Runnable>();var service=new AiOperationService(repository,new ObjectMapper(),transactions,scheduled::add,List.of(handler));
        var user=new User();user.setId(UUID.randomUUID());
        for(boolean commit:List.of(false,true)) {
            TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                service.submit(user,"LANGUAGE_DIAGNOSIS","LANGUAGE_REPORT",UUID.randomUUID(),Map.of(),1,"等待",UUID.randomUUID().toString());
                assertTrue(scheduled.isEmpty());
                if(commit) TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            } finally { TransactionSynchronizationManager.clear(); }
        }
        assertEquals(1,scheduled.size());
    }
}
