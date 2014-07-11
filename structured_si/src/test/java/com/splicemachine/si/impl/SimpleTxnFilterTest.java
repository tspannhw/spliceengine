package com.splicemachine.si.impl;

import com.google.common.collect.Maps;
import com.splicemachine.constants.SIConstants;
import com.splicemachine.constants.SpliceConstants;
import com.splicemachine.encoding.Encoding;
import com.splicemachine.si.SimpleTimestampSource;
import com.splicemachine.si.api.*;
import com.splicemachine.utils.ByteSlice;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Map;

import static com.splicemachine.si.impl.TxnTestUtils.getMockDataStore;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * @author Scott Fines
 * Date: 6/23/14
 */
public class SimpleTxnFilterTest {

		@Test
		public void testCanSeeCommittedRowSnapshotIsolation() throws Exception {
				final Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				//add a committed transaction to the map
				Txn committed = new CommittedTxn(0,1);
				txnMap.put(0l, committed);

				ReadResolver noopResolver = mock(ReadResolver.class);
				DataStore ds = getMockDataStore();

				Txn myTxn = new InheritingTxnView(Txn.ROOT_TRANSACTION,2l,2l, Txn.IsolationLevel.SNAPSHOT_ISOLATION, Txn.State.ACTIVE);
				SimpleTxnFilter filterState = new SimpleTxnFilter(baseStore,myTxn,
								noopResolver,ds);

				KeyValue testCommitKv = new KeyValue(Encoding.encode("1"), SpliceConstants.DEFAULT_FAMILY_BYTES,
								SIConstants.SNAPSHOT_ISOLATION_COMMIT_TIMESTAMP_COLUMN_BYTES, 0l,Bytes.toBytes(1l));

				Filter.ReturnCode code = filterState.filterKeyValue(testCommitKv);
				Assert.assertEquals("Incorrect return code for commit keyvalue!", Filter.ReturnCode.SKIP, code);

				KeyValue testDataKv = new KeyValue(Encoding.encode("1"),SpliceConstants.DEFAULT_FAMILY_BYTES,
								SpliceConstants.PACKED_COLUMN_BYTES,0l,Encoding.encode("hello"));

				Filter.ReturnCode returnCode = filterState.filterKeyValue(testDataKv);
				Assert.assertEquals("Incorrect return code for data keyvalue!", Filter.ReturnCode.INCLUDE,returnCode);
		}

		@Test
		public void testCannotSeeRolledBackRow() throws Exception {
				final Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				//add a rolledBack transaction to the map
				Txn rolledBack = new RolledBackTxn(0l);
				txnMap.put(0l, rolledBack);

				DataStore ds = getMockDataStore();

				Txn myTxn = new InheritingTxnView(Txn.ROOT_TRANSACTION,2l,2l, Txn.IsolationLevel.SNAPSHOT_ISOLATION, Txn.State.ACTIVE);
				SimpleTxnFilter filterState = new SimpleTxnFilter(baseStore,myTxn,
								mock(ReadResolver.class),ds);

				KeyValue testDataKv = new KeyValue(Encoding.encode("1"),SpliceConstants.DEFAULT_FAMILY_BYTES,
								SpliceConstants.PACKED_COLUMN_BYTES,0l,Encoding.encode("hello"));

				Filter.ReturnCode returnCode = filterState.filterKeyValue(testDataKv);
				Assert.assertEquals("Incorrect return code for data keyvalue!", Filter.ReturnCode.SKIP,returnCode);
		}

		/*Tests that Read-Resolution doesn't happen with active transactions*/

		@Test
		public void testWillNotReadResolveActiveTransaction() throws Exception {
				/*
				 * Tests that data written by an active transaction will not read-resolve
				 */
				Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				Txn active = new WritableTxn(1l,1l, Txn.IsolationLevel.SNAPSHOT_ISOLATION,null,mock(TxnLifecycleManager.class),false,false);
				txnMap.put(active.getTxnId(),active);

				assertActive(baseStore,active,2l);
		}

		@Test
		public void testWillNotReadResolveActiveTransactionWithCommittedDependentChild() throws Exception {
				/*
				 * Tests that data written by an active transaction will not read-resolve, even if
				 * it was written by a child transaction which was committed.
				 */
				Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				Txn active = new WritableTxn(1l,1l, Txn.IsolationLevel.SNAPSHOT_ISOLATION,null,mock(TxnLifecycleManager.class),false,false);
				Txn child = new InheritingTxnView(active,2l,2l, Txn.IsolationLevel.SNAPSHOT_ISOLATION,true,true,true,true,true,true,3l,3l, Txn.State.COMMITTED);
				txnMap.put(active.getTxnId(),active);
				txnMap.put(child.getTxnId(),child);

				assertActive(baseStore, child,5l);
		}

		/*Tests for Read-Resolution of Committed transactions*/
		@Test
		public void testWillReadResolveCommittedTransaction() throws Exception {
				/*
				 * Tests that data written by a committed transaction will read-resolve
				 */
				Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				Txn committed = new CommittedTxn(1,2);
				txnMap.put(committed.getTxnId(),committed);

				assertCommitted(baseStore,committed,5l);
		}

		@Test
		public void testWillReadResolveCommittedDependentChildTransaction() throws Exception {
				/*
				 * Tests that data written by a committed transaction will read-resolve a transaction
				 * as committed if its parent is committed
				 */
				Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				Txn parentTxn = new CommittedTxn(1,4);
				Txn committed = new InheritingTxnView(parentTxn,2l,2l,
								Txn.IsolationLevel.SNAPSHOT_ISOLATION,
								true,true,true,true,true,true,3,3, Txn.State.COMMITTED);
				txnMap.put(committed.getTxnId(),committed);
				txnMap.put(parentTxn.getTxnId(),parentTxn);

				assertCommitted(baseStore, committed,5l);
		}

		@Test
		public void testWillReadResolveActiveDependentChildOfCommittedParent() throws Exception {
				/*
				 * Tests that data written by a dependent child transaction will
				 * read-resolve as committed if its parent is committed and it is NOT rolled back
				 */
				Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				Txn parentTxn = new CommittedTxn(1,4);
				Txn committed = new InheritingTxnView(parentTxn,2l,2l,
								Txn.IsolationLevel.SNAPSHOT_ISOLATION,
								true,true,true,true,true,true,3,3, Txn.State.ACTIVE);
				txnMap.put(committed.getTxnId(),committed);
				txnMap.put(parentTxn.getTxnId(),parentTxn);

				assertCommitted(baseStore, committed,5l);
		}

		/*Tests around read-resolution of rolled back transactions*/
		@Test
		public void testWillReadResolveRolledBackTransaction() throws Exception {
				/*
				 * Tests that data written by a rolled-back transaction will read-resolve as rolled back.
				 */
				Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				Txn rolledBackTxn = new RolledBackTxn(1);
				txnMap.put(rolledBackTxn.getTxnId(),rolledBackTxn);

				assertRolledBack(baseStore,rolledBackTxn);
		}


		@Test
		public void testWillReadResolveActiveDependentChildOfRolledBackParent() throws Exception {
				/*
				 * Tests that data written by a dependent child transaction will be rolled back if
				 * the parent has been rolled back, if the child itself is still active
				 */
				Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				Txn parenTxn = new RolledBackTxn(1l);
				Txn rolledBackTxn = new InheritingTxnView(parenTxn,2l,2l,
								Txn.IsolationLevel.SNAPSHOT_ISOLATION, true,true,true,true,true,true,-1l,-1l, Txn.State.ACTIVE);
				txnMap.put(rolledBackTxn.getTxnId(),rolledBackTxn);
				txnMap.put(parenTxn.getTxnId(),parenTxn);

				assertRolledBack(baseStore, rolledBackTxn);
		}



		@Test
		public void testWillReadResolveDependentChildOfRolledBackParent() throws Exception {
				/*
				 * Tests that data written by a dependent child transaction will be rolled back if
				 * the parent has been rolled back, even if the child itself has committed
				 */
				Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				Txn parenTxn = new RolledBackTxn(1l);
				Txn rolledBackTxn = new InheritingTxnView(parenTxn,2l,2l,
								Txn.IsolationLevel.SNAPSHOT_ISOLATION, true,true,true,true,true,true,3l,3l, Txn.State.COMMITTED);
				txnMap.put(rolledBackTxn.getTxnId(),rolledBackTxn);
				txnMap.put(parenTxn.getTxnId(),parenTxn);

				assertRolledBack(baseStore, rolledBackTxn);
		}

		@Test
		public void testWillReadResolveRolledBackDependentChildTransaction() throws Exception {
				/*
				 * Tests that data written by a rolled-back transaction will read-resolve as rolled back,
				 * even if that transaction is the child of a transaction which has been committed
				 */
				Map<Long,Txn> txnMap = Maps.newHashMap();
				TxnSupplier baseStore = getMapStore(txnMap);

				Txn parenTxn = new CommittedTxn(1l,3l);
				Txn rolledBackTxn = new InheritingTxnView(parenTxn,2l,2l,
								Txn.IsolationLevel.SNAPSHOT_ISOLATION, true,true,true,true,true,true,-1l,-1l, Txn.State.ROLLEDBACK);
				txnMap.put(rolledBackTxn.getTxnId(),rolledBackTxn);
				txnMap.put(parenTxn.getTxnId(),parenTxn);

				assertRolledBack(baseStore,rolledBackTxn);
		}

		/******************************************************************************************************************/
		/*private helper methods*/


		private TxnSupplier getMapStore(final Map<Long, Txn> txnMap) throws IOException {
				TxnSupplier baseStore = mock(TxnSupplier.class);
				when(baseStore.getTransaction(anyLong())).thenAnswer(new Answer<Txn>() {
						@Override
						public Txn answer(InvocationOnMock invocationOnMock) throws Throwable {
								//noinspection SuspiciousMethodCalls
								return txnMap.get(invocationOnMock.getArguments()[0]);
						}
				});
				when(baseStore.getTransaction(anyLong(),anyBoolean())).thenAnswer(new Answer<Txn>() {
						@Override
						public Txn answer(InvocationOnMock invocationOnMock) throws Throwable {
								//noinspection SuspiciousMethodCalls
								return txnMap.get(invocationOnMock.getArguments()[0]);
						}
				});
				return baseStore;
		}

		private ReadResolver getRollBackReadResolver(final Pair<ByteSlice, Long> rolledBackTs) {
				ReadResolver resolver = mock(ReadResolver.class);

				doAnswer(new Answer<Void>() {
						@Override
						public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
								Object[] args = invocationOnMock.getArguments();
								rolledBackTs.setFirst((ByteSlice) args[0]);
								rolledBackTs.setSecond((Long) args[1]);
								return null;
						}
				}).when(resolver).resolve(any(ByteSlice.class), anyLong());
				return resolver;
		}

		private ReadResolver getCommitReadResolver(final Pair<ByteSlice, Pair<Long, Long>> committedTs,final TxnSupplier txnStore) {
				ReadResolver resolver = mock(ReadResolver.class);

				doAnswer(new Answer<Void>() {
						@Override
						public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
								Object[] args = invocationOnMock.getArguments();
								committedTs.setFirst((ByteSlice)args[0]);
                long tx = (Long)args[1];
                long commitTs = txnStore.getTransaction(tx).getEffectiveCommitTimestamp();
								committedTs.setSecond(Pair.newPair(tx,commitTs));
								return null;
						}
				}).when(resolver).resolve(any(ByteSlice.class),anyLong());
				return resolver;
		}

		private ReadResolver getActiveReadResolver(){
				ReadResolver resolver = mock(ReadResolver.class);
				doThrow(new AssertionError("Attempted to resolve an entry as committed!"))
								.when(resolver)
								.resolve(any(ByteSlice.class), anyLong());
				return resolver;
		}

		private void assertRolledBack(TxnSupplier baseStore, Txn rolledBackTxn) throws IOException {
				DataStore ds = getMockDataStore();
				Txn myTxn = new InheritingTxnView(Txn.ROOT_TRANSACTION,4l,4l,Txn.IsolationLevel.SNAPSHOT_ISOLATION,Txn.State.ACTIVE);

				final Pair<ByteSlice,Long> rolledBackTs = new Pair<ByteSlice,Long >();
				ReadResolver resolver = getRollBackReadResolver(rolledBackTs);

				SimpleTxnFilter filter = new SimpleTxnFilter(baseStore,myTxn,resolver,ds);

				KeyValue testDataKv = getKeyValue(rolledBackTxn);

				Filter.ReturnCode returnCode = filter.filterKeyValue(testDataKv);
				Assert.assertEquals("Incorrect return code for data keyvalue!", Filter.ReturnCode.SKIP, returnCode);

				Assert.assertNotNull("ReadResolver was not told to rollback!", rolledBackTs.getFirst());

				ByteSlice first = rolledBackTs.getFirst();
				Assert.assertArrayEquals("Incorrect row to resolve rolledBackTxn!",testDataKv.getRow(),first.getByteCopy());

				long rolledBackTxnId = rolledBackTs.getSecond();
				Assert.assertEquals("Incorrect version of data to be rolled back!",rolledBackTxn.getTxnId(),rolledBackTxnId);
		}

		private void assertCommitted(TxnSupplier baseStore, Txn committed,long readTs) throws IOException {
				DataStore ds = getMockDataStore();
				Txn myTxn = new InheritingTxnView(Txn.ROOT_TRANSACTION,readTs,readTs,Txn.IsolationLevel.SNAPSHOT_ISOLATION,Txn.State.ACTIVE);

				final Pair<ByteSlice,Pair<Long,Long>> committedTs = new Pair<ByteSlice, Pair<Long, Long>>();
				ReadResolver resolver = getCommitReadResolver(committedTs,baseStore);

				SimpleTxnFilter filter = new SimpleTxnFilter(baseStore,myTxn,resolver,ds);

				KeyValue testDataKv = getKeyValue(committed);

				Filter.ReturnCode returnCode = filter.filterKeyValue(testDataKv);
				Assert.assertEquals("Incorrect return code for data keyvalue!", Filter.ReturnCode.INCLUDE, returnCode);

				Assert.assertNotNull("ReadResolver was not told to commit!",committedTs.getFirst());

				ByteSlice first = committedTs.getFirst();
				Assert.assertArrayEquals("Incorrect row to resolve committed!",testDataKv.getRow(),first.getByteCopy());

				Pair<Long,Long> txnIdToCommitTs = committedTs.getSecond();
				Assert.assertEquals("Incorrect transaction id!",committed.getTxnId(),txnIdToCommitTs.getFirst().longValue());
				Assert.assertEquals("Incorrect commit timestamp!",committed.getEffectiveCommitTimestamp(),
								txnIdToCommitTs.getSecond().longValue());
		}


		private void assertActive(TxnSupplier baseStore, Txn active,long readTs) throws IOException{
				DataStore ds = getMockDataStore();
				Txn myTxn = new ReadOnlyTxn(readTs,readTs, Txn.IsolationLevel.SNAPSHOT_ISOLATION,null,mock(TxnLifecycleManager.class),false,false);

				ReadResolver resolver = getActiveReadResolver();

				SimpleTxnFilter filter = new SimpleTxnFilter(baseStore,myTxn,resolver,ds);

				KeyValue testDataKv = getKeyValue(active);

				Filter.ReturnCode returnCode = filter.filterKeyValue(testDataKv);
				Assert.assertEquals("Incorrect return code for data keyValue!", Filter.ReturnCode.SKIP,returnCode);

				//the read-resolver will ensure that an error is thrown if we attempt to read-resolve
		}

		private KeyValue getKeyValue(Txn txn) {
				return new KeyValue(Encoding.encode("1"), SpliceConstants.DEFAULT_FAMILY_BYTES,
								SpliceConstants.PACKED_COLUMN_BYTES,txn.getTxnId(),Encoding.encode("hello"));
		}

}