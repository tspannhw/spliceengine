package com.splicemachine.derby.test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * DDL test cases
 * 
 * @author jessiezhang
 */

public class OperationStatsTest extends SpliceDerbyTest {
	private static Logger LOG = Logger.getLogger(OperationStatsTest.class);

	@BeforeClass 
	public static void startup() throws Exception {
		startConnection();		
		Statement s = null;
		CallableStatement cs = null;
		try {
			conn.setAutoCommit(true);
			s = conn.createStatement();
			s.execute("create table a (i int)");
			s.execute("create table b (j int)");
			s.execute("insert into a values 1,2,5,2,54,6,57,6,6,86,555657,787,78894,2,324,4,3,44,4556,7,32,43,43545,46,565765,34,54,65664,34,45,3,3,35,5,6");
			s.execute("insert into b values 1,34,5,3,45,6,7,8,5,3,23,2,3,33,2,2,2,2,4,4,54,65,66767,678,78,7565,543434,56,657,6767,54,3345,6,755,3,65664,33,54,5,6,565765,43,433,43,43,434,6,6,54,44,2,5,4,3,3,4");
			s.execute("create table c (k int)");
			s.execute("insert into c values 1,2");
			
			cs = conn.prepareCall("CALL SYSCS_UTIL.SYSCS_SET_RUNTIMESTATISTICS(1)");
			cs.execute();
			
			cs = conn.prepareCall("CALL SYSCS_UTIL.SYSCS_SET_STATISTICS_TIMING(1)");
			cs.execute();
			
			cs = conn.prepareCall("CALL SYSCS_UTIL.SYSCS_SET_XPLAIN_SCHEMA('MYSCHEMA')");
			cs.execute();
		} catch (SQLException e) {
			LOG.error("error during create and drop table-"+e.getMessage(), e);
		} finally {
			try {
				if (s!= null)
					s.close();
				if (cs != null)
					cs.close();
			} catch (SQLException e) {
				//no need to print out
			}
		}
	}
	
	@Test
	public void testGroupAggregateJoinStats() throws SQLException {
		Statement s = null;
		CallableStatement cs = null;
		ResultSet rs = null;
		try {
			s = conn.createStatement();
			long start = System.nanoTime();
			rs = s.executeQuery("select i, count(i) from a left outer join b on i=j group by i order by i");
			LOG.info(">>>>>>>>>total time="+(System.nanoTime() - start));
			start = System.nanoTime();
			while (rs.next()) {
				rs.getInt(1);
				LOG.info(">>>>>>>>>next time="+(System.nanoTime() - start));
				start = System.nanoTime();
			}
			
			cs = conn.prepareCall("CALL SYSCS_UTIL.SYSCS_SET_RUNTIMESTATISTICS(0)");
			cs.execute();
			
			rs = s.executeQuery("select * from myschema.SYSXPLAIN_STATEMENT_TIMINGS");
			while (rs.next()) {
				LOG.info(rs.getString(1));
			}
		} catch (SQLException e) {
			LOG.error("error during testGroupAggregateJoinStats-"+e.getMessage(), e);
		} finally {
			try {
				if (s!= null)
					s.close();
				if (cs != null)
					cs.close();
			} catch (SQLException e) {
				//no need to print out
			}
		}
	}	
	
	@Test
	public void testSelectStats() throws SQLException {
		PreparedStatement s = null;
		CallableStatement cs = null;
		ResultSet rs = null;
		try {
			s = conn.prepareStatement("select i from a where i > ?");
			s.setInt(1, 10);
			s.execute();
			
			cs = conn.prepareCall("CALL SYSCS_UTIL.SYSCS_SET_RUNTIMESTATISTICS(0)");
			cs.execute();
			
			rs = s.executeQuery("select * from myschema.SYSXPLAIN_STATEMENT_TIMINGS");
			while (rs.next()) {
				LOG.info(rs.getString(1));
			}
		} catch (SQLException e) {
			LOG.error("error during testGroupAggregateJoinStats-"+e.getMessage(), e);
		} finally {
			try {
				if (s!= null)
					s.close();
				if (cs != null)
					cs.close();
			} catch (SQLException e) {
				//no need to print out
			}
		}
	}	
	
	@Test
	public void testSimpleSelectStats() throws SQLException {
		PreparedStatement s = null;
		ResultSet rs = null;
		try {
			System.setProperty("","");
			long start = System.nanoTime();
			s = conn.prepareStatement("select k from c");
			rs = s.executeQuery();
			LOG.info(">>>>>>>>>total time="+(System.nanoTime() - start));
			start = System.nanoTime();
			while (rs.next()) {
				rs.getInt(1);
				LOG.info(">>>>>>>>>next time="+(System.nanoTime() - start));
				start = System.nanoTime();
			}
		} catch (SQLException e) {
			LOG.error("error during create and insert table-"+e.getMessage(), e);
		} finally {
			try {
				if (s!= null)
					s.close();
			} catch (SQLException e) {
				//no need to print out
			}
		}
	}	

	@AfterClass 
	public static void shutdown() throws SQLException {
		dropTable("a");
		dropTable("b") ;
		dropTable("c") ;
		stopConnection();		
	}
}
