package com.splicemachine.derby.hbase;

import com.google.common.base.Function;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.google.protobuf.Service;
import com.google.protobuf.ZeroCopyLiteralByteString;
import com.splicemachine.access.HConfiguration;
import com.splicemachine.access.api.ServerControl;
import com.splicemachine.concurrent.SystemClock;
import com.splicemachine.constants.EnvUtils;
import com.splicemachine.coprocessor.SpliceMessage;
import com.splicemachine.lifecycle.DatabaseLifecycleManager;
import com.splicemachine.lifecycle.PipelineLoadService;
import com.splicemachine.pipeline.PartitionWritePipeline;
import com.splicemachine.pipeline.PipelineDriver;
import com.splicemachine.pipeline.PipelineEnvironment;
import com.splicemachine.pipeline.PipelineWriter;
import com.splicemachine.pipeline.client.BulkWrites;
import com.splicemachine.pipeline.client.BulkWritesResult;
import com.splicemachine.pipeline.contextfactory.ContextFactoryDriver;
import com.splicemachine.pipeline.utils.PipelineCompressor;
import com.splicemachine.si.data.hbase.coprocessor.TableType;
import com.splicemachine.si.impl.HNotServingRegion;
import com.splicemachine.si.impl.region.RegionServerControl;
import com.splicemachine.storage.RegionPartition;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.hadoop.hbase.Coprocessor;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.NotServingRegionException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.util.StringUtils;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Endpoint to allow special batch operations that the HBase API doesn't explicitly enable
 * by default (such as bulk-processed mutations)
 *
 * @author Scott Fines
 *         Created on: 3/11/13
 */
public class SpliceIndexEndpoint extends SpliceMessage.SpliceIndexService implements BatchProtocol,Coprocessor, IndexEndpoint{
    private static final Logger LOG=Logger.getLogger(SpliceIndexEndpoint.class);

    private PartitionWritePipeline writePipeline;
    private PipelineWriter pipelineWriter;
    private PipelineCompressor compressor;
    private PipelineLoadService<TableName> service;



    @Override
    @SuppressWarnings("unchecked")
    public void start(final CoprocessorEnvironment env) throws IOException{
        RegionCoprocessorEnvironment rce=((RegionCoprocessorEnvironment)env);
        final ServerControl serverControl=new RegionServerControl(rce.getRegion());

        String tableName=rce.getRegion().getTableDesc().getTableName().getQualifierAsString();
        TableType table=EnvUtils.getTableType(HConfiguration.INSTANCE,(RegionCoprocessorEnvironment)env);
        if(table.equals(TableType.USER_TABLE) || table.equals(TableType.DERBY_SYS_TABLE)){ // DERBY SYS TABLE is temporary (stats)
            long conglomId;
            try{
                conglomId=Long.parseLong(tableName);
            }catch(NumberFormatException nfe){
                SpliceLogUtils.warn(LOG,"Unable to parse conglomerate id for table %s, "+
                        "index management for batch operations will be disabled",tableName);
                conglomId=-1;
            }
            final long cId = conglomId;
            final RegionPartition baseRegion=new RegionPartition(rce.getRegion());

            try{
                service=new PipelineLoadService<TableName>(serverControl,baseRegion,cId){

                    @Override
                    public void start() throws Exception{
                        super.start();
                        compressor=getCompressor();
                        pipelineWriter=getPipelineWriter();
                        writePipeline=getWritePipeline();
                    }

                    @Override
                    public void shutdown() throws Exception{
                        super.shutdown();
                    }

                    @Override
                    protected Function<TableName, String> getStringParsingFunction(){
                        return new Function<TableName, String>(){
                            @Nullable
                            @Override
                            public String apply(TableName tableName){
                                return tableName.getNameAsString();
                            }
                        };
                    }

                    @Override
                    protected PipelineEnvironment loadPipelineEnvironment(ContextFactoryDriver cfDriver) throws IOException{
                        return HBasePipelineEnvironment.loadEnvironment(new SystemClock(),cfDriver);
                    }
                };
                DatabaseLifecycleManager.manager().registerGeneralService(service);
            }catch(Exception e){
                throw new IOException(e);
            }
        }
    }

    @Override
    public SpliceIndexEndpoint getBaseIndexEndpoint(){
        return this;
    }

    @Override
    public void bulkWrite(RpcController controller,
                          SpliceMessage.BulkWriteRequest request,
                          RpcCallback<SpliceMessage.BulkWriteResponse> done){
        try{
            byte[] bytes=bulkWrites(request.getBytes().toByteArray());
            if(bytes==null||bytes.length<=0)
                LOG.error("No bytes constructed for the result!");

            SpliceMessage.BulkWriteResponse response =SpliceMessage.BulkWriteResponse.newBuilder()
                    .setBytes(ZeroCopyLiteralByteString.wrap(bytes)).build();
            done.run(response);
        }catch(IOException e){
            LOG.error("Unexpected exception performing bulk write: ",e);
            controller.setFailed(StringUtils.stringifyException(e));
        }
    }

    @Override
    public Service getService(){
        return this;
    }

    @Override
    public void stop(CoprocessorEnvironment env) throws IOException{
        if(writePipeline!=null){
            writePipeline.close();
            PipelineDriver.driver().deregisterPipeline(((RegionCoprocessorEnvironment)env).getRegion().getRegionNameAsString());
        }
        if(service!=null)
            try{
                service.shutdown();
            }catch(Exception e){
                throw new IOException(e);
            }
    }

    @Override
    public BulkWritesResult bulkWrite(BulkWrites bulkWrites) throws IOException{
        return pipelineWriter.bulkWrite(bulkWrites);
    }

    @Override
    public byte[] bulkWrites(byte[] bulkWriteBytes) throws IOException{
        assert bulkWriteBytes!=null;
        BulkWrites bulkWrites=compressor.decompress(bulkWriteBytes,BulkWrites.class);
        return compressor.compress(bulkWrite(bulkWrites));
    }
}