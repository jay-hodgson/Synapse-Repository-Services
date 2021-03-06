package org.sagebionetworks.table.worker;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sagebionetworks.StackConfiguration;
import org.sagebionetworks.common.util.progress.ProgressCallback;
import org.sagebionetworks.common.util.progress.ProgressingCallable;
import org.sagebionetworks.repo.manager.table.TableEntityManager;
import org.sagebionetworks.repo.manager.table.TableIndexConnectionFactory;
import org.sagebionetworks.repo.manager.table.TableIndexConnectionUnavailableException;
import org.sagebionetworks.repo.manager.table.TableIndexManager;
import org.sagebionetworks.repo.manager.table.TableManagerSupport;
import org.sagebionetworks.repo.model.ConflictingUpdateException;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.dbo.dao.table.TableModelTestUtils;
import org.sagebionetworks.repo.model.message.ChangeMessage;
import org.sagebionetworks.repo.model.message.ChangeType;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.RowSet;
import org.sagebionetworks.repo.model.table.TableRowChange;
import org.sagebionetworks.repo.model.table.TableStatus;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.table.worker.TableWorker.State;
import org.sagebionetworks.workers.util.aws.message.RecoverableMessageException;
import org.sagebionetworks.workers.util.semaphore.LockUnavilableException;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.collect.Lists;

public class TableWorkerTest {
	
	@Mock
	ProgressCallback<ChangeMessage> mockProgressCallback;
	@Mock
	TableEntityManager mockTableEntityManager;
	@Mock
	TableIndexManager mockTableIndexManager;
	@Mock
	TableIndexConnectionFactory mockConnectionFactory;
	@Mock
	StackConfiguration mockConfiguration;
	@Mock
	TableManagerSupport mockTableManagerSupport;

	TableWorker worker;
	ChangeMessage one;
	ChangeMessage two;
	String tableId;
	String resetToken;
	List<ColumnModel> currentSchema;
	RowSet rowSet1;
	RowSet rowSet2;
	
	@Before
	public void before() throws LockUnavilableException, InterruptedException, Exception{
		MockitoAnnotations.initMocks(this);
		// Turn on the feature by default
		when(mockConfiguration.getTableEnabled()).thenReturn(true);
		when(mockConnectionFactory.connectToTableIndex(anyString())).thenReturn(mockTableIndexManager);
		
		// By default we want to the manager to just call the passed callable.
		stub(mockTableManagerSupport.tryRunWithTableExclusiveLock(any(ProgressCallback.class),anyString(), anyInt(), any(ProgressingCallable.class))).toAnswer(new Answer<TableWorker.State>() {
			@Override
			public TableWorker.State answer(InvocationOnMock invocation) throws Throwable {
				ProgressingCallable<TableWorker.State, ChangeMessage> callable = (ProgressingCallable<State, ChangeMessage>) invocation.getArguments()[3];
				if(callable != null){
					return callable.call(mockProgressCallback);
				}else{
					return null;
				}
			}
		});
		worker = new TableWorker();
		ReflectionTestUtils.setField(worker, "connectionFactory", mockConnectionFactory);
		ReflectionTestUtils.setField(worker, "tableEntityManager", mockTableEntityManager);
		ReflectionTestUtils.setField(worker, "configuration", mockConfiguration);
		ReflectionTestUtils.setField(worker, "tableManagerSupport", mockTableManagerSupport);
		worker.setTimeoutSeconds(1200L);
		
		one = new ChangeMessage();
		one.setChangeType(ChangeType.CREATE);
		one.setObjectId("123");
		one.setObjectType(ObjectType.ACTIVITY);
		one.setObjectEtag("etag");
		
		two = new ChangeMessage();
		two.setChangeType(ChangeType.CREATE);
		two.setObjectId("456");
		two.setObjectType(ObjectType.ACTIVITY);
		two.setObjectEtag("etag");
		
		
		tableId = "456";
		resetToken = "reset-token";
		currentSchema = Lists.newArrayList();
		when(mockTableManagerSupport.getColumnModelsForTable(tableId)).thenReturn(currentSchema);
		TableRowChange trc1 = new TableRowChange();
		trc1.setEtag("etag");
		trc1.setRowVersion(0L);
		trc1.setRowCount(12L);
		TableRowChange trc2 = new TableRowChange();
		trc2.setEtag("etag2");
		trc2.setRowVersion(1L);
		trc2.setRowCount(3L);
		when(mockTableEntityManager.listRowSetsKeysForTable(tableId)).thenReturn(Arrays.asList(trc1,trc2));
		when(mockTableIndexManager.isVersionAppliedToIndex(anyLong())).thenReturn(false);
		
		rowSet1 = new RowSet();
		rowSet1.setRows(Collections.singletonList(TableModelTestUtils.createRow(0L, 0L, "2")));
		when(mockTableEntityManager.getRowSet(eq(tableId), eq(0L), anyListOf(ColumnModel.class))).thenReturn(rowSet1);
		
		rowSet2 = new RowSet();
		rowSet2.setRows(Collections.singletonList(TableModelTestUtils.createRow(0L, 1L, "3")));
		when(mockTableEntityManager.getRowSet(eq(tableId), eq(1L), anyListOf(ColumnModel.class))).thenReturn(rowSet2);
		
		when(mockTableManagerSupport.startTableProcessing(tableId)).thenReturn(resetToken);
		
		when(mockTableManagerSupport.isIndexWorkRequired(tableId)).thenReturn(true);
	}
	
	
	/**
	 * Non-table messages should simply be returned so they can be removed from the queue.
	 * @throws Exception
	 */
	@Test
	public void testNonTableMessages() throws Exception{
		// call under test
		worker.run(mockProgressCallback, one);
		worker.run(mockProgressCallback, two);
		// The connection factory should never be called
		verifyZeroInteractions(mockConnectionFactory);
	}
	
	@Test (expected=IllegalStateException.class)
	public void testLockTimeoutNotSet() throws Exception{
		worker = new TableWorker();
		ReflectionTestUtils.setField(worker, "connectionFactory", mockConnectionFactory);
		ReflectionTestUtils.setField(worker, "tableEntityManager", mockTableEntityManager);
		ReflectionTestUtils.setField(worker, "configuration", mockConfiguration);
		// call under test
		worker.run(mockProgressCallback, one);
	}
	
	/**
	 * When the table feature is disabled all messages should be removed from the queue and no other methods should be called.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFeatureDisabled() throws Exception{
		// Disable the feature
		when(mockConfiguration.getTableEnabled()).thenReturn(false);
		one.setObjectType(ObjectType.TABLE);
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.DELETE);
		// call under test
		worker.run(mockProgressCallback, one);
		worker.run(mockProgressCallback, two);
		// The connection factory should never be called
		verifyZeroInteractions(mockConnectionFactory);
	}
	
	/**
	 * A delete message should result in the table index also being deleted.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testTableDelete() throws Exception{
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.DELETE);
		// call under test
		worker.run(mockProgressCallback, two);
		// The connection factory should be called
		verify(mockConnectionFactory, times(1)).connectToTableIndex(two.getObjectId());
		// delete should be called
		verify(mockTableIndexManager, times(1)).deleteTableIndex();
	}
	
	/**
	 * When everything works well, the message should be removed from the queue and 
	 * the table status should be set to AVAILABLE.
	 * @throws Exception
	 */
	@Test
	public void testHappyCase() throws Exception{
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		// call under test
		worker.run(mockProgressCallback, two);
		// The worker should ensure the table is processing.
		verify(mockTableManagerSupport, times(1)).startTableProcessing(tableId);
		// The connection factory should be called
		verify(mockConnectionFactory, times(1)).connectToTableIndex(tableId);
		// The status should get set to available
		verify(mockTableManagerSupport, times(1)).attemptToSetTableStatusToAvailable(tableId, resetToken, "etag2");
		verify(mockTableManagerSupport, times(4)).attemptToUpdateTableProgress(eq(tableId), eq(resetToken), anyString(), anyLong(), anyLong());
		
		verify(mockTableIndexManager).applyChangeSetToIndex(rowSet1, currentSchema, 0L);
		verify(mockTableIndexManager).applyChangeSetToIndex(rowSet2, currentSchema, 1L);
		// Progress should be made for each result
		verify(mockProgressCallback, times(2)).progressMade(two);
	}
	
	@Test
	public void testOneChangeAlreadyApplied() throws Exception{
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		// For this case v0 is already applied to the index, while v1 is not.
		when(mockTableIndexManager.isVersionAppliedToIndex(0L)).thenReturn(true);
		when(mockTableIndexManager.isVersionAppliedToIndex(1L)).thenReturn(false);
		// call under test
		worker.run(mockProgressCallback, two);
		
		verify(mockTableEntityManager, never()).getRowSet(eq(tableId), eq(0L), anyListOf(ColumnModel.class));
		verify(mockTableIndexManager).applyChangeSetToIndex(rowSet2, currentSchema, 1L);
		
		// Progress should be made for each change even if there is no work.
		verify(mockProgressCallback, times(2)).progressMade(two);
	}
	
	/**
	 * When everything works well, the message should be removed from the queue and the table status should be set to
	 * AVAILABLE.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testHappyUpdateCase() throws Exception {
		String tableId = "456";
		String resetToken = "reset-token";
		TableStatus status = new TableStatus();
		status.setResetToken(resetToken);
		List<ColumnModel> currentSchema = Lists.newArrayList();
		when(mockTableManagerSupport.getColumnModelsForTable(tableId)).thenReturn(currentSchema);
		when(mockTableManagerSupport.getTableStatusOrCreateIfNotExists(tableId)).thenReturn(status);
		TableRowChange trc = new TableRowChange();
		trc.setEtag("etag");
		trc.setRowVersion(3L);
		trc.setRowCount(12L);
		when(mockTableEntityManager.listRowSetsKeysForTable(tableId)).thenReturn(Arrays.asList(trc));
		when(mockTableIndexManager.isVersionAppliedToIndex(trc.getRowVersion())).thenReturn(false);
		RowSet rowSet = new RowSet();
		rowSet.setRows(Collections.singletonList(TableModelTestUtils.createRow(0L, 3L, "2")));
		when(mockTableEntityManager.getRowSet(eq(tableId), eq(3L), anyListOf(ColumnModel.class))).thenReturn(rowSet);
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		// call under test
		worker.run(mockProgressCallback, two);
		// The connection factory should be called
		verify(mockConnectionFactory, times(1)).connectToTableIndex(tableId);
		// The status should get set to available
		verify(mockTableManagerSupport, times(1)).attemptToSetTableStatusToAvailable(tableId, resetToken, "etag");
		
		verify(mockTableIndexManager).applyChangeSetToIndex(rowSet, currentSchema, trc.getRowVersion());
	}
	
	/**
	 * When an unknown exception is thrown the table status must get set to failed.
	 * @throws Exception
	 */
	@Test
	public void testSetToFailed() throws Exception{
		String tableId = "456";
		String resetToken = "reset-token";
		TableStatus status = new TableStatus();
		status.setResetToken(resetToken);
		when(mockTableManagerSupport.getTableStatusOrCreateIfNotExists(tableId)).thenReturn(status);
		// This should trigger a failure
		RuntimeException error = new RuntimeException("Something went horribly wrong!");
		when(mockTableManagerSupport.getColumnModelsForTable(tableId)).thenThrow(error);
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		// call under test
		worker.run(mockProgressCallback, two);
		// The connection factory should be called
		verify(mockConnectionFactory, times(1)).connectToTableIndex(tableId);
		// The status should get set to failed
		verify(mockTableManagerSupport, times(1)).attemptToSetTableStatusToFailed(anyString(), anyString(), anyString(), anyString());
	}
	
	/**
	 * If a connection to the cluster cannot be made at this time, then the message must return the queue
	 * as we can recover from this error when a database becomes available.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testNoConnection() throws Exception{
		String tableId = "456";
		String resetToken = "reset-token";
		TableStatus status = new TableStatus();
		status.setResetToken(resetToken);
		when(mockTableManagerSupport.getTableStatusOrCreateIfNotExists(tableId)).thenReturn(status);
		// Without a connection the message should go back to the queue
		when(mockConnectionFactory.connectToTableIndex(tableId)).thenThrow(new TableIndexConnectionUnavailableException("Not now"));
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		two.setObjectId(tableId);
		try {
			// call under test
			worker.run(mockProgressCallback, two);
			fail();
		} catch (RecoverableMessageException e) {
			// expected
		}
	}
	
	/**
	 * When a NotFoundException is thrown the table no longer exists and the message should be removed from the queue.
	 * @throws Exception
	 */
	@Test
	public void testNotFoundException() throws Exception{
		String tableId = "456";
		String resetToken = "reset-token";
		TableStatus status = new TableStatus();
		status.setResetToken(resetToken);
		when(mockTableManagerSupport.startTableProcessing(tableId)).thenThrow(new NotFoundException("This table does not exist"));
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		// call under test
		worker.run(mockProgressCallback, two);
		// The index connection should not be used.
		verifyZeroInteractions(mockTableIndexManager);
	}
	
	/**
	 * When a lock cannot be acquired, the message should remain on the queue as this is a recoverable failure.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testLockUnavilableException() throws Exception{
		String tableId = "456";
		String resetToken = "reset-token";
		TableStatus status = new TableStatus();
		status.setResetToken(resetToken);
		when(mockTableManagerSupport.getTableStatusOrCreateIfNotExists(tableId)).thenReturn(status);
		// Simulate a failure to get the lock
		when(mockTableManagerSupport.tryRunWithTableExclusiveLock(any(ProgressCallback.class),anyString(), anyInt(), any(ProgressingCallable.class))).thenThrow(new LockUnavilableException("Cannot get a lock at this time"));
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		try {
			// call under test
			worker.run(mockProgressCallback, two);
			fail();
		} catch (RecoverableMessageException e) {
			// expected
		}
		// The index connection should not be used.
		verifyZeroInteractions(mockTableIndexManager);
	}

	/**
	 * An InterruptedException thrown while waiting for lock should be treated asn a recoverable exception.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testInterruptedException() throws Exception{
		String tableId = "456";
		String resetToken = "reset-token";
		TableStatus status = new TableStatus();
		status.setResetToken(resetToken);
		when(mockTableManagerSupport.getTableStatusOrCreateIfNotExists(tableId)).thenReturn(status);
		// Simulate a failure to get the lock
		when(mockTableManagerSupport.tryRunWithTableExclusiveLock(any(ProgressCallback.class),anyString(), anyInt(), any(ProgressingCallable.class))).thenThrow(new InterruptedException("Sop!!!"));
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		try {
			// call under test
			worker.run(mockProgressCallback, two);
			fail();
		} catch (RecoverableMessageException e) {
			// expected
		}
		// The index connection should not be used.
		verifyZeroInteractions(mockTableIndexManager);
	}
	
	/**
	 * A ConflictingUpdateException indicates that the table was update after the original message was sent.
	 * This is treated like a non-recoverable error and the message should be removed from the queue.
	 * @throws Exception
	 */
	@Test
	public void testConflictingUpdateException() throws Exception{
		String tableId = "456";
		String resetToken = "reset-token";
		TableStatus status = new TableStatus();
		status.setResetToken(resetToken);
		// simulate the ConflictingUpdateException
		doThrow(new ConflictingUpdateException("Cannot get a lock at this time")).when(mockTableManagerSupport).attemptToSetTableStatusToAvailable(anyString(), anyString(), anyString());
		when(mockTableManagerSupport.getTableStatusOrCreateIfNotExists(tableId)).thenReturn(status);
		two.setObjectId(tableId);
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		// call under test
		worker.run(mockProgressCallback, two);
		// The connection factory should never be called
		verify(mockTableManagerSupport).attemptToSetTableStatusToAvailable(anyString(), anyString(), anyString());
	}
	
	/**
	 * This case the first change set is broken but the second change set fixes it.
	 * @throws Exception
	 */
	@Test
	public void testBrokenChangeSetFixed() throws Exception{
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		
		// Set the first change set to have an error.
		doThrow(new RuntimeException("Bad Change Set")).when(mockTableIndexManager).applyChangeSetToIndex(rowSet1, currentSchema, 0L);
		
		// call under test
		worker.run(mockProgressCallback, two);
		// The connection factory should be called
		verify(mockConnectionFactory, times(1)).connectToTableIndex(tableId);
		// The status should get set to available
		verify(mockTableManagerSupport, times(1)).attemptToSetTableStatusToAvailable(tableId, resetToken, "etag2");
		verify(mockTableManagerSupport, times(4)).attemptToUpdateTableProgress(eq(tableId), eq(resetToken), anyString(), anyLong(), anyLong());

		verify(mockTableIndexManager).applyChangeSetToIndex(rowSet2, currentSchema, 1L);
		// Progress should be made for each result
		verify(mockProgressCallback, times(2)).progressMade(two);
	}
	
	/**
	 * For this case the second (and last) change set is broken so the job should fail.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testBrokenChangeSetNotFixed() throws Exception{
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		
		// this time the second change set has an error
		RuntimeException error = new RuntimeException("Bad Change Set");
		doThrow(error).when(mockTableIndexManager).applyChangeSetToIndex(rowSet2, currentSchema, 1L);
		
		// call under test
		worker.run(mockProgressCallback, two);
		
		verify(mockTableIndexManager).applyChangeSetToIndex(rowSet1, currentSchema, 0L);
		
		// The status should get set to failed
		verify(mockTableManagerSupport, times(1)).attemptToSetTableStatusToFailed(anyString(), anyString(), anyString(), anyString());
	}
	
	/**
	 * For this case, there are multiple errors without a fix so the job should fail.
	 * @throws Exception
	 */
	@Test
	public void testBrokenChangeSetMultipleErrors() throws Exception{
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		
		// this time the second change set has an error
		RuntimeException error1 = new RuntimeException("Bad Change Set 1");
		RuntimeException error2 = new RuntimeException("Bad Change Set 2");
		doThrow(error1).when(mockTableIndexManager).applyChangeSetToIndex(rowSet1, currentSchema, 0L);
		doThrow(error2).when(mockTableIndexManager).applyChangeSetToIndex(rowSet2, currentSchema, 1L);
		
		// call under test
		worker.run(mockProgressCallback, two);
		
		verify(mockTableIndexManager).applyChangeSetToIndex(rowSet1, currentSchema, 0L);
		
		// The status should get set to failed
		verify(mockTableManagerSupport, times(1)).attemptToSetTableStatusToFailed(anyString(), anyString(), contains(error2.getMessage()), anyString());
	}
	
	@Test
	public void testNoWork() throws Exception {
		// setup no work
		when(mockTableManagerSupport.isIndexWorkRequired(tableId)).thenReturn(false);
		two.setObjectType(ObjectType.TABLE);
		two.setChangeType(ChangeType.UPDATE);
		two.setObjectEtag(resetToken);
		// call under test
		worker.run(mockProgressCallback, two);
		// no work should be performed.
		verifyZeroInteractions(mockTableIndexManager);
	}
}
