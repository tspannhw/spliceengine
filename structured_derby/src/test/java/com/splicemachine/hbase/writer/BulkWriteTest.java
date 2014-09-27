package com.splicemachine.hbase.writer;

import com.carrotsearch.hppc.IntObjectOpenHashMap;
import com.carrotsearch.hppc.ObjectArrayList;
import com.splicemachine.encoding.Encoding;
import com.splicemachine.hbase.KVPair;
import com.splicemachine.si.api.TxnSupplier;
import com.splicemachine.si.impl.ActiveWriteTxn;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;

/**
 * @author Scott Fines
 * Date: 12/10/13
 */
public class BulkWriteTest {

		@Test
		public void testCanEncodeAndDecodeResultCorrectly() throws Exception {
				BulkWriteResult result = new BulkWriteResult();
				result.addResult(1, WriteResult.failed("Testing failure"));

				byte[] bytes = result.toBytes();

				BulkWriteResult decoded = BulkWriteResult.fromBytes(bytes);

				IntObjectOpenHashMap<WriteResult> failedRows = result.getFailedRows();
				Assert.assertNotNull("Incorrect failed rows list!", failedRows.get(1));
				WriteResult writeResult = failedRows.get(1);
				Assert.assertEquals("Incorrect write result!","Testing failure", writeResult.getErrorMessage());
				Assert.assertEquals("Incorrect write result!", WriteResult.Code.FAILED,writeResult.getCode());
		}

		@Test
		public void testCanEncodeAndDecodeWriteCorrectly() throws Exception {
				ObjectArrayList<KVPair> list = new ObjectArrayList<KVPair>();
				KVPair kvPair = new KVPair(Encoding.encode("Hello"),new byte[]{}, KVPair.Type.DELETE);
				list.add(kvPair);
				BulkWrite write = new BulkWrite(list,new ActiveWriteTxn(1l,1l),null);

				byte[] bytes = write.toBytes();

				BulkWrite decoded = BulkWrite.fromBytes(bytes,mock(TxnSupplier.class));

				ObjectArrayList<KVPair> decList = decoded.getMutations();
				KVPair decPair = decList.get(0);
				Assert.assertEquals("Incorrect pair!","Hello",Encoding.decodeString(decPair.getRow()));
		}
}
