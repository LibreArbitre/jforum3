package net.jforum;

import java.lang.reflect.Field;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import junit.framework.TestCase;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

public class JForumExecutionContextTest extends TestCase
{
        private DBConnection originalConnection;

        protected void setUp() throws Exception
        {
                super.setUp();
                this.originalConnection = this.getCurrentImplementation();
                SystemGlobals.setValue(ConfigKeys.DATABASE_USE_TRANSACTIONS, "true");
        }

        protected void tearDown() throws Exception
        {
                super.tearDown();
                JForumExecutionContext.finish();

                if (this.originalConnection != null) {
                        this.setImplementation(this.originalConnection);
                }
        }

        public void testCommitPathReleasesConnection()
        {
                TrackingDBConnection trackingConnection = new TrackingDBConnection();
                this.setImplementation(trackingConnection);

                try (JForumExecutionContext.ExecutionContext context = JForumExecutionContext.start()) {
                        JForumExecutionContext.getConnection();
                }

                assertTrue(trackingConnection.getTrackingConnection().commitCalled);
                assertEquals(1, trackingConnection.releaseCount);
        }

        public void testRollbackPathReleasesConnectionOnException()
        {
                TrackingDBConnection trackingConnection = new TrackingDBConnection();
                this.setImplementation(trackingConnection);

                try {
                        try (JForumExecutionContext.ExecutionContext context = JForumExecutionContext.start()) {
                                JForumExecutionContext.getConnection();
                                JForumExecutionContext.enableRollback();
                                throw new RuntimeException("boom");
                        }
                }
                catch (RuntimeException e) {
                        // expected
                }

                assertTrue(trackingConnection.getTrackingConnection().rollbackCalled);
                assertEquals(1, trackingConnection.releaseCount);
        }

        private void setImplementation(DBConnection impl)
        {
                try {
                        Field f = DBConnection.class.getDeclaredField("instance");
                        f.setAccessible(true);
                        f.set(null, impl);
                }
                catch (Exception e) {
                        throw new RuntimeException(e);
                }
        }

        private DBConnection getCurrentImplementation()
        {
                try {
                        Field f = DBConnection.class.getDeclaredField("instance");
                        f.setAccessible(true);
                        return (DBConnection)f.get(null);
                }
                catch (Exception e) {
                        return null;
                }
        }

        private static class TrackingDBConnection extends DBConnection
        {
                private final TrackingConnection connection = new TrackingConnection();
                private int releaseCount;

                public void init() {}

                public Connection getConnection()
                {
                        return this.connection;
                }

                public void releaseConnection(Connection conn)
                {
                        this.releaseCount++;
                }

                public void realReleaseAllConnections() throws Exception {}

                public TrackingConnection getTrackingConnection()
                {
                        return this.connection;
                }
        }

        private static class TrackingConnection implements Connection
        {
                private boolean autoCommit;
                private boolean closed;
                private boolean commitCalled;
                private boolean rollbackCalled;

                public void clearWarnings() {}

                public void close()
                {
                        this.closed = true;
                }

                public void commit()
                {
                        this.commitCalled = true;
                }

                public Statement createStatement() { throw new UnsupportedOperationException(); }

                public Statement createStatement(int resultSetType, int resultSetConcurrency) { throw new UnsupportedOperationException(); }

                public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) { throw new UnsupportedOperationException(); }

                public boolean getAutoCommit()
                {
                        return this.autoCommit;
                }

                public String getCatalog() { throw new UnsupportedOperationException(); }

                public int getHoldability() { throw new UnsupportedOperationException(); }

                public DatabaseMetaData getMetaData() { throw new UnsupportedOperationException(); }

                public int getTransactionIsolation() { throw new UnsupportedOperationException(); }

                public Map getTypeMap() { throw new UnsupportedOperationException(); }

                public SQLWarning getWarnings() { throw new UnsupportedOperationException(); }

                public boolean isClosed()
                {
                        return this.closed;
                }

                public boolean isReadOnly() { throw new UnsupportedOperationException(); }

                public boolean isValid(int timeout) { throw new UnsupportedOperationException(); }

                public String nativeSQL(String sql) { throw new UnsupportedOperationException(); }

                public CallableStatement prepareCall(String sql) { throw new UnsupportedOperationException(); }

                public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) { throw new UnsupportedOperationException(); }

                public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { throw new UnsupportedOperationException(); }

                public PreparedStatement prepareStatement(String sql) { throw new UnsupportedOperationException(); }

                public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) { throw new UnsupportedOperationException(); }

                public PreparedStatement prepareStatement(String sql, int[] columnIndexes) { throw new UnsupportedOperationException(); }

                public PreparedStatement prepareStatement(String sql, String[] columnNames) { throw new UnsupportedOperationException(); }

                public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) { throw new UnsupportedOperationException(); }

                public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { throw new UnsupportedOperationException(); }

                public void releaseSavepoint(Savepoint savepoint) { throw new UnsupportedOperationException(); }

                public void rollback()
                {
                        this.rollbackCalled = true;
                }

                public void rollback(Savepoint savepoint)
                {
                        this.rollback();
                }

                public void setAutoCommit(boolean autoCommit)
                {
                        this.autoCommit = autoCommit;
                }

                public void setCatalog(String catalog) { throw new UnsupportedOperationException(); }

                public void setHoldability(int holdability) { throw new UnsupportedOperationException(); }

                public void setReadOnly(boolean readOnly) { throw new UnsupportedOperationException(); }

                public Savepoint setSavepoint() { throw new UnsupportedOperationException(); }

                public Savepoint setSavepoint(String name) { throw new UnsupportedOperationException(); }

                public void setTransactionIsolation(int level) { throw new UnsupportedOperationException(); }

                public void setTypeMap(Map map) { throw new UnsupportedOperationException(); }

                public boolean isWrapperFor(Class iface) { return false; }

                public Object unwrap(Class iface) { throw new UnsupportedOperationException(); }

                public void abort(Executor executor) {}

                public void setNetworkTimeout(Executor executor, int milliseconds) {}

                public int getNetworkTimeout() { return 0; }

                public void beginRequest() {}

                public void endRequest() {}

                public boolean setShardingKeyIfValid(ShardingKey shardingKey, ShardingKey superShardingKey, int timeout)
                {
                        return false;
                }

                public void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey) {}

                public boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout)
                {
                        return false;
                }

                public void setShardingKey(ShardingKey shardingKey) {}

                public Clob createClob() { throw new UnsupportedOperationException(); }

                public Blob createBlob() { throw new UnsupportedOperationException(); }

                public NClob createNClob() { throw new UnsupportedOperationException(); }

                public SQLXML createSQLXML() { throw new UnsupportedOperationException(); }

                public boolean isValid() { return true; }

                public void setClientInfo(String name, String value) throws SQLClientInfoException { }

                public void setClientInfo(Properties properties) throws SQLClientInfoException { }

                public String getClientInfo(String name) { throw new UnsupportedOperationException(); }

                public Properties getClientInfo() { throw new UnsupportedOperationException(); }

                public Array createArrayOf(String typeName, Object[] elements) { throw new UnsupportedOperationException(); }

                public Struct createStruct(String typeName, Object[] attributes) { throw new UnsupportedOperationException(); }

                public void setSchema(String schema) {}

                public String getSchema() { return null; }
        }
}
