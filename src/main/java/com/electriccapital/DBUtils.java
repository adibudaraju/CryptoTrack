package com.electriccapital;

/**
 * Database utility class that does the SQL heavy-lifting. Most public methods in here take in queries
 * in the form of a String and spit out results in the form of a ResultSet, String, or Integer.
 */

import net.dv8tion.jda.api.entities.*;
import org.slf4j.*;
import org.springframework.jdbc.support.*;

import java.sql.*;
import java.sql.Date;

public class DBUtils{
    private static final Logger LOGGER = LoggerFactory.getLogger(DBUtils.class);

    /**
     * Utility method to get a results set from a String query.
     * @param conn
     * @param statement
     * @param query
     * @return
     */
    public static ResultSet getResults(Connection conn, Statement statement, String query){
        try {
            return statement.executeQuery(query);
        }
        catch (SQLException e){
            logError(e);
            return null;
        }
    }

    /**
     * Same purpose as getResults but is used when trying to avoid SQL injection.
     * @param conn
     * @param ps
     * @param args
     * @return
     */
    public static ResultSet getResultsSafe(Connection conn, PreparedStatement ps, Object... args){
        try {
            mapParams(ps, args);
            return ps.executeQuery();
        }
        catch (SQLException e){
            logError(e);
            return null;
        }
    }

    /**
     * Utility method to quickly print a debug error message
     * @param e
     */
    public static void logError(Exception e){
        LOGGER.error("Couldn't execute query!", e);
        e.printStackTrace();
    }

    /**
     * Gets integer result from a query, typically a COUNT query, given a column label.
     * @param query
     * @param colLabel
     * @return
     */
    public static int getIntResult(String query, String colLabel){
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            ResultSet results = getResults(conn, statement, query);
            int val = results.getInt(colLabel);
            close(results, statement, conn);
            return val;
        }
        catch (SQLException e){
            logError(e);
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Gets integer result from a query, typically a COUNT query, given a column index.
     * @param query
     * @param colNum
     * @return
     */
    public static int getIntResult(String query, int colNum){
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            ResultSet results = getResults(conn, statement, query);
            int val = results.getInt(colNum);
            close(results, statement, conn);
            return val;
        }
        catch (SQLException e){
            logError(e);
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Gets JDBC connection.
     * @return
     */
    public static Connection getConnection(){
        Connection conn;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:src/main/java/com/electriccapital/activity.db");
        } catch (Exception e) {
            conn = null;
            LOGGER.error("Couldn't connect to database", e);
        }
        return conn;
    }

    /**
     * Gets String result from a query, typically a COUNT query, given a column label.
     * @param query
     * @param colLabel
     * @return
     */
    public static String getStringResult(String query, String colLabel) {
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            ResultSet results = getResults(conn, statement, query);
            String val = results.getString(colLabel);
            close(results, statement, conn);
            return val;
        }
        catch (SQLException e){
            logError(e);
            return null;
        }
    }

    /**
     * Gets String result from a query, typically a COUNT query, given a column index.
     * @param query
     * @param colNum
     * @return
     */
    public static String getStringResult(String query, int colNum) {
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            ResultSet results = getResults(conn, statement, query);
            String val = results.getString(colNum);
            close(results, statement, conn);
            return val;
        }
        catch (SQLException e){
            logError(e);
            return null;
        }
    }

    /**
     * Executes a list of queries, prone to SQL injection so only used for safe or local queries.
     * @param queries
     * @return
     */
    public static boolean executeLocalOnly(String... queries) {
        try {
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            boolean flag = true;
            for (String query : queries)
                if(!statement.execute(query)) flag = false;
            close(statement, conn);
            return flag;
        } catch (SQLException e) {
            logError(e);
            return false;
        }

    }

    /**
     * Executes a query using a PreparedStatement
     * @param query
     * @param params
     */
    public static void executePrepared(String query, Object... params){
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(query);
            mapParams(ps, params);
            ps.execute();
            close(ps, conn);
        } catch (SQLException e) {
            logError(e);
        }
    }

    /**
     * Maps parameters onto a PreparedStatement
     * @param ps
     * @param params
     */
    public static void mapParams(PreparedStatement ps, Object... params){
        int i = 1;
        for (Object arg : params) {
            try {
                if (arg instanceof Date) {
                    ps.setTimestamp(i++, new Timestamp(((Date) arg).getTime()));
                } else if (arg instanceof Integer) {
                    ps.setInt(i++, (Integer) arg);
                } else if (arg instanceof Long) {
                    ps.setLong(i++, (Long) arg);
                } else if (arg instanceof Double) {
                    ps.setDouble(i++, (Double) arg);
                } else if (arg instanceof Float) {
                    ps.setFloat(i++, (Float) arg);
                } else {
                    ps.setString(i++, (String) arg);
                }
            }
            catch (SQLException e){
                logError(e);
            }
        }
    }

    /**
     * Checks if the database contains a given text channel
     * @param channel
     * @return
     */
    public static boolean containsChannel(TextChannel channel){
        String query = "SELECT count(1) from channels WHERE channelID = " + channel.getIdLong();
        return getIntResult(query, 1)!=0;
    }

    /**
     * Checks if the database contains a given user given the object
     * @param user
     * @return
     */
    public static boolean containsUser(User user){
        String query = "SELECT count(1) from members WHERE userID = " + user.getIdLong();
        return getIntResult(query, 1)!=0;
    }


    /**
     * Checks if the database contains a given user given their ID
     * @param id
     * @return
     */
    public static boolean containsUser(long id){
        String query = "SELECT count(1) from members WHERE userID = " + id;
        return getIntResult(query, 1)!=0;
    }

    /**
     * Closes JDBC-related stuff
     * @param statement
     */
    public static void close(Statement statement) {
        JdbcUtils.closeStatement(statement);
    }

    /**
     * Closes JDBC-related stuff
     * @param conn
     */
    public static void close(Connection conn){
        JdbcUtils.closeConnection(conn);
    }

    /**
     * Closes JDBC-related stuff
     * @param results
     */
    public static void close(ResultSet results){
        JdbcUtils.closeResultSet(results);
    }

    /**
     * Closes JDBC-related stuff
     * @param results
     * @param statement
     * @param conn
     */
    public static void close(ResultSet results, Statement statement, Connection conn){
        close(results);
        close(statement);
        close(conn);
    }

    /**
     * Closes JDBC-related stuff
     * @param statement
     * @param conn
     */
    public static void close(Statement statement, Connection conn){
        close(statement);
        close(conn);
    }

}
