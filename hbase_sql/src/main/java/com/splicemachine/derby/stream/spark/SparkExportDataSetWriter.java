package com.splicemachine.derby.stream.spark;

import com.splicemachine.access.HConfiguration;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.types.SQLInteger;
import com.splicemachine.db.impl.sql.execute.ValueRow;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.impl.SpliceSpark;
import com.splicemachine.derby.impl.sql.execute.operations.LocatedRow;
import com.splicemachine.derby.stream.function.SpliceFunction2;
import com.splicemachine.derby.stream.iapi.DataSet;
import com.splicemachine.derby.stream.iapi.TableWriter;
import com.splicemachine.derby.stream.output.DataSetWriter;
import com.splicemachine.derby.stream.output.ExportDataSetWriterBuilder;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.utils.ByteDataOutput;
import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Job;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.*;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author Scott Fines
 *         Date: 1/8/16
 */
public class SparkExportDataSetWriter<V> implements DataSetWriter{
    private String directory;
    private SpliceFunction2<? extends SpliceOperation, OutputStream, Iterator<V>, Integer> exportFunction;
    private JavaRDD<V> rdd;

    public SparkExportDataSetWriter(){
    }

    public SparkExportDataSetWriter(JavaRDD<V> rdd,
                                    String directory,
                                    SpliceFunction2<? extends SpliceOperation, OutputStream, Iterator<V>, Integer> exportFunction){
        this.directory=directory;
        this.rdd = rdd;
        this.exportFunction=exportFunction;
    }

//    @Override
    public void writeExternal(ObjectOutput out) throws IOException{
        out.writeUTF(directory);
        out.writeObject(exportFunction);
        out.writeObject(rdd);
    }

//    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{
        directory = in.readUTF();
        exportFunction = (SpliceFunction2)in.readObject();
        rdd = (JavaRDD)in.readObject();
    }

    @Override
    public DataSet<LocatedRow> write() throws StandardException{
        Configuration conf=new Configuration(HConfiguration.INSTANCE.unwrapDelegate());
        ByteDataOutput bdo=new ByteDataOutput();
        Job job;
        String encoded;

        try{
            bdo.writeObject(exportFunction);
            encoded=Base64.encodeBase64String(bdo.toByteArray());
            conf.set("exportFunction",encoded);

            job=Job.getInstance(conf);
        }catch(IOException e){
            throw new RuntimeException(e);
        }
        job.setOutputKeyClass(Void.class);
        job.setOutputValueClass(LocatedRow.class);
        job.setOutputFormatClass(SparkDataSet.EOutputFormat.class);
        job.getConfiguration().set("mapred.output.dir",directory);

        JavaRDD<V> cached=rdd.cache();
        int writtenRows=(int)cached.count();
        rdd.keyBy(new NullFunction<V>()).saveAsNewAPIHadoopDataset(job.getConfiguration());
        cached.unpersist();

        JavaSparkContext ctx=SpliceSpark.getContext();
        ValueRow valueRow=new ValueRow(2);
        valueRow.setColumn(1,new SQLInteger(writtenRows));
        valueRow.setColumn(2,new SQLInteger(0));
        return new SparkDataSet<>(ctx.parallelize(Collections.singletonList(new LocatedRow(valueRow)),1));
    }

    @Override
    public void setTxn(TxnView childTxn){
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public TableWriter getTableWriter() throws StandardException{
        return null;
    }

    @Override
    public TxnView getTxn(){
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public byte[] getDestinationTable(){
        throw new UnsupportedOperationException();
    }

    public static class Builder<V> implements ExportDataSetWriterBuilder{
        private String directory;
        private SpliceFunction2<? extends SpliceOperation,OutputStream,Iterator<V>,Integer> exportFunction;
        private JavaRDD<V> rdd;

        public Builder(JavaRDD<V> rdd){
            this.rdd=rdd;
        }

        @Override
        public ExportDataSetWriterBuilder directory(String directory){
            this.directory = directory;
            return this;
        }

        @Override
        public /*<Op extends SpliceOperation>*/ ExportDataSetWriterBuilder exportFunction(SpliceFunction2/*<Op,OutputStream,Iterator<V>, Integer>*/ exportFunction){
            this.exportFunction = exportFunction;
            return this;
        }


        @Override
        public DataSetWriter build(){
            return new SparkExportDataSetWriter<>(rdd,directory,exportFunction);
        }
    }

    public static class NullFunction<V> implements org.apache.spark.api.java.function.Function<V, Object>{
        public NullFunction(){ }

        @Override
        public Object call(V v) throws Exception{
            return null;
        }
    }
}