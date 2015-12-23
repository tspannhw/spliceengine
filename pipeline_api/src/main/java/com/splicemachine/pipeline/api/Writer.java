package com.splicemachine.pipeline.api;

import com.splicemachine.pipeline.client.BulkWrites;
import com.splicemachine.pipeline.config.WriteConfiguration;

import javax.management.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Interface for performing physical writes
 * 
 * @author Scott Fines
 * Created on: 8/8/13
 */
public interface Writer {

    Future<WriteStats> write(byte[] tableName,BulkWrites action,WriteConfiguration writeConfiguration) throws ExecutionException;

    void stopWrites();

    void registerJMX(MBeanServer mbs) throws MalformedObjectNameException,NotCompliantMBeanException,InstanceAlreadyExistsException,MBeanRegistrationException;

}
