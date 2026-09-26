package com.ainovel.app.v2;

import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.v2.model.V2ExportJob;
import com.ainovel.app.v2.repo.V2ExportJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class V2ExportDownloadBudgetTest {
    @Test void boundedCopyRejectsOversizeBeforeWritingExtraBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> V2ExportJobService.copyBounded(new ByteArrayInputStream(new byte[9]), output, 8));
        assertTrue(output.size() <= 8);
    }
    @Test void connectionIsReleasedBeforeSlowBodyAndDisconnectReleasesQuota() throws Exception {
        var jobs = mock(V2ExportJobRepository.class); var jdbc = mock(JdbcTemplate.class);
        var service = new V2ExportJobService(mock(V2ExportPersistenceService.class), jobs, mock(UserRepository.class),
                mock(V2ExportRenderer.class), jdbc, mock(TransactionTemplate.class), Runnable::run);
        User user = new User(); ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        UUID manuscriptId = UUID.randomUUID(), id = UUID.randomUUID();
        V2ExportJob job = new V2ExportJob(); job.setUser(user); job.setStatus("completed"); job.setChecksum("hash"); job.setFileName("test.txt"); job.setFormat("txt"); job.setFileSizeBytes(3L);
        when(jobs.findByManuscriptIdAndId(manuscriptId, id)).thenReturn(Optional.of(job));
        Connection connection = mock(Connection.class); PreparedStatement statement = mock(PreparedStatement.class); ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement); when(statement.executeQuery()).thenReturn(result); when(result.next()).thenReturn(true);
        when(result.getBinaryStream(1)).thenAnswer(i -> new ByteArrayInputStream(new byte[]{1,2,3}));
        AtomicBoolean dbActive = new AtomicBoolean();
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(i -> {
            dbActive.set(true); try { return ((ConnectionCallback<?>) i.getArgument(0)).doInConnection(connection); }
            finally { dbActive.set(false); }
        });
        var first = service.download(manuscriptId,id,user); var second = service.download(manuscriptId,id,user);
        assertThrows(RuntimeException.class, () -> service.download(manuscriptId,id,user));
        assertThrows(IOException.class, () -> first.body().writeTo(new OutputStream() {
            @Override public void write(int value) throws IOException { assertFalse(dbActive.get()); throw new IOException("client disconnected"); }
        }));
        var third = service.download(manuscriptId,id,user);
        second.body().writeTo(OutputStream.nullOutputStream()); third.body().writeTo(OutputStream.nullOutputStream());
        assertEquals(0, ReflectionTestUtils.getField(service,"downloadCount"));
        assertTrue(((Map<?,?>) ReflectionTestUtils.getField(service,"downloads")).isEmpty());
    }
}
