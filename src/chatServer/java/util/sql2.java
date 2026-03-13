package util;

/**
 * Programmed: Ben Coe.(2005)<br />
 * sql.java
 * Used to make calls to an SQL database.
 */

import java.util.*;
import java.sql.*;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class sql2 {
    private boolean connect;
    private Connection c;

    private static String ConfigConnection = null;
    private static String ConfigUsername = null;
    private static String ConfigPassword = null;
    private static int ConfigPort = 0;


    public boolean checkLogin(String userName, String ip, String pass) {
        Pattern pattern = Pattern.compile("\\A[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1}\\.[0-9]{1,3}\\z");
        Matcher matcher = pattern.matcher(ip);
        if (!matcher.matches()) return false;
        if (userName.contains("\"")) return false;
        if (userName.contains("\'")) return false;
        if (pass.contains("\"")) return false;
        if (pass.contains("\'")) return false;


        ArrayList result = null;
        String Q = "";
        Q = "SELECT password FROM hackerforum.users WHERE name = \"" + userName + "\" AND password = PASSWORD(\"" + pass + "\") AND ip = \"" + ip + "\"";
        result = (ArrayList) this.process(Q);
        this.close();
        if (result == null || result.size() == 0) {
            return (false);
        }
        return true;
    }


    public sql2(String Connection, String DB, String Username, String Password) {

        if (ConfigConnection == null) {
            try {
                BufferedReader BR = new BufferedReader(new FileReader("db.ini"));
                ConfigConnection = BR.readLine();
                ConfigUsername = BR.readLine();
                ConfigPassword = BR.readLine();
                ConfigPort = new Integer(BR.readLine());
            } catch (Exception e) {
                //	e.printStackTrace();
            }

            if (ConfigConnection == null) {
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

        connect = false;

        //Connect to the database.
        c = null;
        try {
            c = openMysqlConnection(Connection, DB, ConfigPort, Username, Password);
            connect = true;
        } catch (Exception e) {
            //Throw a loggable error here
        }
    }

    public sql2() {

    }

    /**
     ArrayList process()<br />
     Returns an ArrayList of strings representing a flattend collection
     of MySql matches. CMD is an SQL query.
     */
    public ArrayList process(String cmd) {
        ArrayList returnMe = null;
        if (connect == false || c == null)
            return (null);

        Statement stmt = null;
        try {
            stmt = this.c.createStatement();
            stmt.setEscapeProcessing(false);
            ResultSet rs = stmt.executeQuery(cmd);
            while (rs.next()) {
                int i = 1;
                boolean or = false;
                while (!or) {
                    try {
                        String s = rs.getString(i);
                        if (returnMe == null)
                            returnMe = new ArrayList();
                        returnMe.add(s);

                        i++;
                    } catch (Exception ore) {
                        or = true;
                    }
                }
            }
        } catch (Exception ee) {
//ee.printStackTrace();
            try {//Maybe it's an update.
                if (stmt != null)
                    stmt.executeUpdate(cmd);
            } catch (Exception ue) {
                //ue.printStackTrace();
            }
        } finally {
            try {
                if (stmt != null)
                    stmt.close();
            } catch (Exception e) {
            }
        }

        return (returnMe);
    }

    public void close() {
        try {
            if (c != null)
                c.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Connection openMysqlConnection(String host, String db, int port, String username, String password) throws Exception {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException oldDriverMissing) {
            Class.forName("com.mysql.cj.jdbc.Driver");
        }
        return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + db, username, password);
    }
}
