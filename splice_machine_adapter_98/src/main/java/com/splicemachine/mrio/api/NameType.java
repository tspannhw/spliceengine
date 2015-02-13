package com.splicemachine.mrio.api;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.types.DataTypeDescriptor;

/**
 * 
 * Class to wrap the name and type of a column for hive SerDe
 *
 */
public class NameType {
	protected String name;
	protected int type;
	
	public NameType() {
		
	}
	
	public NameType(String name, int type) {
		this.name = name;
		this.type = type;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}
	// WTF is this?
    private int getTypeFormatId() throws StandardException{
    	return DataTypeDescriptor.getBuiltInDataTypeDescriptor(1).getNull().getTypeFormatId();
    }
	
}
