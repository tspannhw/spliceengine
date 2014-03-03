package com.splicemachine.si.data.hbase;

import com.splicemachine.si.impl.RowAccumulator;
import com.splicemachine.storage.EntryAccumulator;
import com.splicemachine.storage.EntryDecoder;
import com.splicemachine.storage.EntryPredicateFilter;
import com.splicemachine.storage.index.BitIndex;

import java.io.IOException;

import org.apache.hadoop.hbase.KeyValue;

public class HRowAccumulator implements RowAccumulator {
    private final EntryPredicateFilter predicateFilter;
    private final EntryAccumulator entryAccumulator;
    private final EntryDecoder decoder;
    private boolean countStar;

		private long bytesAccumulated = 0l;

    public HRowAccumulator(EntryPredicateFilter predicateFilter, EntryDecoder decoder, boolean countStar) {
        this.predicateFilter = predicateFilter;
        this.entryAccumulator = predicateFilter.newAccumulator();
        this.decoder = decoder;
        this.countStar = countStar;
    }

    @Override
    public boolean isOfInterest(KeyValue keyValue) {
    	if (countStar)
    		return false;
        decoder.set(keyValue.getBuffer(),keyValue.getValueOffset(),keyValue.getValueLength());
        final BitIndex currentIndex = decoder.getCurrentIndex();
		return entryAccumulator.isInteresting(currentIndex);
    }

    @Override
    public boolean accumulate(KeyValue keyValue) throws IOException {
		bytesAccumulated+=keyValue.getLength();
        decoder.set(keyValue.getBuffer(),keyValue.getValueOffset(),keyValue.getValueLength());
        boolean pass = predicateFilter.match(decoder, entryAccumulator);
        if(!pass)
            entryAccumulator.reset();
        return pass;
    }

    @Override
    public boolean isFinished() {
    	if (countStar)
    		return true;
        return entryAccumulator.isFinished();
    }

    @Override
    public byte[] result() {
        final byte[] result = entryAccumulator.finish();
        entryAccumulator.reset();
        return result;
    }
	@Override public long getBytesVisited() { return bytesAccumulated; }
	
	@Override
	public boolean isCountStar() {
		return this.countStar;
	}
}
