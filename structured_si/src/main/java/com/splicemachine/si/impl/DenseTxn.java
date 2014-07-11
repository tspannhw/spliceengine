package com.splicemachine.si.impl;

import com.splicemachine.si.api.Txn;
import com.splicemachine.utils.ByteSlice;

/**
 * @author Scott Fines
 *         Date: 7/9/14
 */
public class DenseTxn extends SparseTxn {
    private final long lastKATime;

    public DenseTxn(long txnId,
                    long beginTimestamp,
                    long parentTxnId,
                    long commitTimestamp,
                    long globalCommitTimestamp,
                    boolean hasDependentField, boolean dependent,
                    boolean hasAdditiveField, boolean additive,
                    Txn.IsolationLevel isolationLevel,
                    Txn.State state,
                    ByteSlice destTableBuffer,
                    long lastKeepAliveTime) {
        super(txnId, beginTimestamp, parentTxnId,
                commitTimestamp, globalCommitTimestamp,
                hasDependentField, dependent,
                hasAdditiveField, additive, isolationLevel, state, destTableBuffer);
        this.lastKATime = lastKeepAliveTime;
    }

    public long getLastKATime() {
        return lastKATime;
    }
}