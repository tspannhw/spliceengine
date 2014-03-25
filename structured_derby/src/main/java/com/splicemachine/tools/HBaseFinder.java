package com.splicemachine.tools;

import java.util.Arrays;

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.ObjectArrayList;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.util.Bytes;

import com.splicemachine.constants.SpliceConstants;
import com.splicemachine.derby.hbase.SpliceDriver;
import com.splicemachine.derby.utils.marshall.RowMarshaller;
import com.splicemachine.encoding.Encoding;
import com.splicemachine.encoding.debug.DataType;
import com.splicemachine.hbase.CellUtils;
import com.splicemachine.storage.EntryAccumulator;
import com.splicemachine.storage.EntryDecoder;
import com.splicemachine.storage.EntryPredicateFilter;
import com.splicemachine.storage.Predicate;
import com.splicemachine.storage.ValuePredicate;

/**
 * Debugging utility that allows one to scan a table and look for any row which has an entry in a specific column.
 *
 * In order for this to work, you MUST have the proper HBase connection information (zookeeper address) on your classpath
 * in the form of a well-formed hbase-site.xml file.
 *
 * @author Scott Fines
 * Created on: 9/3/13
 */
public class HBaseFinder {

    public static void main(String... args) throws Exception{
        /*
         * First entry is the table
         * second entry is the column number
         * third entry is the data to find
         * fourth entry is the column type
         */
        if(args.length!=4){
            System.err.println("Unable to understand arguments: "+ Arrays.toString(args));
            System.exit(65);
        }

        String tableName = args[0];
        String colNumber = args[1];
        String data = args[2];
        String dataTypeCode = args[3];
        DataType dataType = null;
        try{
            dataType = DataType.fromCode(dataTypeCode);
        }catch(IllegalArgumentException iae){
            System.err.printf("Invalid type code: %s%n",dataTypeCode);
            System.exit(4);
        }

        //get data bytes
        byte[] dataBytes = dataType.encode(data);

        System.out.println(Bytes.toStringBinary(dataBytes));
        int colNum = -1;
        try{
            colNum = Integer.parseInt(colNumber);
        }catch(NumberFormatException nfe){
            System.err.printf("Invalid column number: %s",colNumber);
            System.exit(3);
        }

        Predicate predicate = new ValuePredicate(CompareFilter.CompareOp.EQUAL,colNum,dataBytes,false);
        EntryPredicateFilter edf = new EntryPredicateFilter(new BitSet(),ObjectArrayList.from(predicate));

        Scan scan = new Scan();
        scan.addFamily(SpliceConstants.DEFAULT_FAMILY_BYTES);
        scan.addColumn(SpliceConstants.DEFAULT_FAMILY_BYTES, RowMarshaller.PACKED_COLUMN_KEY);
        scan.setAttribute(SpliceConstants.ENTRY_PREDICATE_LABEL,edf.toBytes());
        scan.setAttribute(SpliceConstants.SI_EXEMPT, Bytes.toBytes(true));
        scan.setMaxVersions();

        //TODO -sf- read zookeeper info from other place besides classpath?
        Configuration configuration = SpliceConstants.config;

        HTable table = new HTable(configuration,tableName);
        ResultScanner scanner = null;
        try{
            scanner = table.getScanner(scan);
            Result result;
            EntryDecoder entryDecoder = new EntryDecoder(SpliceDriver.getKryoPool());
            EntryAccumulator accumulator = edf.newAccumulator();
            int numResults=0;
            do{
                result = scanner.next();
                if(result==null) continue;
                edf.reset();
                byte[] row = result.getRow();
                String rowBytes = Bytes.toStringBinary(row);
                for(Cell kv:result.rawCells()){
                    if(!CellUtils.matchingColumn(kv,SpliceConstants.DEFAULT_FAMILY_BYTES, RowMarshaller.PACKED_COLUMN_KEY)) continue; //skip non-data columns
                    long ts = kv.getTimestamp();
                    byte[] value = CellUtil.cloneValue(kv);
                    if(value.length<=0) continue;
                    entryDecoder.set(value);
                    if(edf.match(entryDecoder,accumulator)){
                        numResults++;
                        System.out.printf("row:\t%s%n",rowBytes);
                        System.out.printf("encoded row:\t%s%n",Bytes.toStringBinary(Encoding.encodeBytesUnsorted(row)));
                        System.out.printf("\t%-20d\t%s%n",ts,Bytes.toStringBinary(value));
                    }
                }
            }while(result!=null);
            System.out.printf("Returned %d results%n",numResults);
        }finally{
            if(scanner!=null)
                scanner.close();
            table.close();
        }
    }
}
