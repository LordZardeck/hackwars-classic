package util;

/**
Programmed: Ben Coe.(2005)<br />
sql.java
Used to make calls to an SQL database.
*/

import java.util.*;
import java.sql.*;
import java.io.*;

public class sql{
    private boolean connect;
    private Connection c;

	private static String ConfigConnection = null;
	private static String ConfigUsername = null;
	private static String ConfigPassword = null;
	private static int ConfigPort = 0;
	private static final boolean SQL_OPTIONAL=!"false".equalsIgnoreCase(System.getProperty("hackwars.chat.sqlOptional","true"));

    //Constructors.
    /**
    Depricated connects to database using hard-coded variables.
    */
    public sql(){
        connect=false;

        //Connect to the database.
        c=null;
        try{
            c = openMysqlConnection("www.mariealighieri.com","volgate",3306,"deepwater","awa878");
            connect=true;
        }catch(Exception e){
        
        }
    }

    public sql(String Connection,String DB,String Username,String Password){
		if(ConfigConnection == null) {
			try {
				BufferedReader BR = new BufferedReader(new FileReader("db.ini"));
				ConfigConnection = BR.readLine();
				ConfigUsername = BR.readLine();
				ConfigPassword = BR.readLine();
				ConfigPort = new Integer(BR.readLine());
			} catch (Exception e) {
			//	e.printStackTrace();
			}
			
			if(ConfigConnection == null) {
				ConfigConnection = "localhost";
				ConfigUsername = "root";
				ConfigPassword = "";
				ConfigPort = 3306;
			} else {
				System.out.println("Configuration file sucessfully loaded.");
			}
		}
		
		Connection = ConfigConnection;
		Username = ConfigUsername;
		Password = ConfigPassword;
	
        connect=false;

        //Connect to the database.
        c=null;
        try{
            c = openMysqlConnection(Connection,DB,ConfigPort,Username,Password);
            connect=true;
        }catch(Exception e){}
    }

    /**
    ArrayList process()<br />
    Returns an ArrayList of strings representing a flattend collection
    of MySql matches. CMD is an SQL query.
    */
    public ArrayList process(String cmd){
            ArrayList returnMe=null;
            if(connect==false||c==null)
                return(null);

            Statement stmt=null;
            try{
                stmt = this.c.createStatement();
				stmt.setEscapeProcessing(false);
                ResultSet rs = stmt.executeQuery(cmd);
                while (rs.next()){
                    int i=1;
                    boolean or=false;
                    while(!or){
                        try{
                            String s=rs.getString(i);
                            if(returnMe==null)
                                returnMe=new ArrayList();
                            returnMe.add(s);

                            i++;
                        }catch(Exception ore){
                            or=true;
                        }
                    }
                }
            }catch(Exception ee){
                try{//Maybe it's an update.
                	if(stmt!=null)
                    	stmt.executeUpdate(cmd);
                }catch(Exception ue){
                    ue.printStackTrace();
                }
            }finally{
            	try{
            		if(stmt!=null)
            			stmt.close();
            	}catch(Exception e){}
            }

        return(returnMe);
    }
	
    
    /**
    ArrayList processException()<br />
    Returns an ArrayList of strings representing a flattend collection
    of MySql matches. CMD is an SQL query.
    Throws Exceptions instead of stack traces.
    */
	    public ArrayList<String> processQuery(String cmd) throws Exception{
	        ArrayList<String> returnMe=null;
	        if(connect==false||c==null){
	        	if(SQL_OPTIONAL)
	        		return null;
	            throw new Exception("No active SQL connection.");
	        }

        Statement stmt=null;

        try{
	        stmt = this.c.createStatement();
	        stmt.setEscapeProcessing(false);
	        ResultSet rs = stmt.executeQuery(cmd);
	        while (rs.next()){
	            int i=1;
	            boolean or=false;
	            while(!or){
	                try{
	                    String s=rs.getString(i);
	                    if(returnMe==null)
	                        returnMe=new ArrayList<String>();
	                    returnMe.add(s);

	                    i++;
	                }catch(Exception ore){
	                    or=true;
	                }
	            }
	        }
        }finally{
        	try{
        		if(stmt!=null)
        			stmt.close();
        	}catch(Exception e){}
        }


        return(returnMe);
    }
    
    /* Processes change */
	    public boolean processUpdate(String cmd) throws Exception{
	        Statement stmt=null;
	        if(connect==false||c==null){
	        	if(SQL_OPTIONAL)
	        		return true;
	            throw new Exception("No active SQL connection.");
	        }
        try{
            stmt = this.c.createStatement();
            stmt.setEscapeProcessing(false);
            stmt.executeUpdate(cmd);
        }catch(Exception e){
            throw e;
        }finally{
        	try{
        		if(stmt!=null)
        			stmt.close();
        	}catch(Exception e){}
        }
        return true;
    }
    
    public void close(){
            try{
                    if(c!=null)
                    	c.close();
            }catch(Exception e){
                    e.printStackTrace();
            }
    }

    private Connection openMysqlConnection(String host,String db,int port,String username,String password) throws Exception{
        try{
            Class.forName("com.mysql.jdbc.Driver");
        }catch(ClassNotFoundException oldDriverMissing){
            Class.forName("com.mysql.cj.jdbc.Driver");
        }
        return DriverManager.getConnection("jdbc:mysql://"+host+":"+port+"/"+db,username,password);
    }
}
