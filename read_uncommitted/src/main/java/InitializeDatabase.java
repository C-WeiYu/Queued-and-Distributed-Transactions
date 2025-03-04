import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class InitializeDatabase {
    private String server;
    private String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private String databaseURL ;
    private Statement stmt;
    private String createTableSQL = "CREATE TABLE DB(Item int , Num int);" ;
    private String createTxLogSQL = "CREATE TABLE TxLog(Id int auto_increment primary key , Transaction nvarchar(50) , Item int , Num int);";

    void setDatabaseURL(String serverName){
        this.databaseURL = "jdbc:mysql://localhost:3306/"+serverName+"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    void startSQL(String JDBC, String DB_URL) throws ClassNotFoundException, SQLException {
        Class.forName(JDBC);
        Connection conn= DriverManager.getConnection(DB_URL,"root","nccutest");
        stmt =conn.createStatement();

    }

    void createDatabase() throws SQLException {
        String initializeSQL1 = String.format("CREATE DATABASE Server_1;") ;
        stmt.execute(initializeSQL1);
        String initializeSQL2 = String.format("CREATE DATABASE Server_2;") ;
        stmt.execute(initializeSQL2);

    }

    void initial() throws SQLException {
        stmt.execute(createTableSQL);
        for (int i = 1 ; i <= 100 ; i++){
            String initializeSQL = String.format("INSERT INTO DB(Item,Num) VALUES(%d,%d)",i,i) ;
            stmt.execute(initializeSQL);
        }
        stmt.execute(createTxLogSQL);

    }

    public static void main(String[] args) throws SQLException, ClassNotFoundException {
        InitializeDatabase initializeDatabase0 = new InitializeDatabase();
        initializeDatabase0.setDatabaseURL("mysql");
        initializeDatabase0.startSQL(initializeDatabase0.JDBC_DRIVER, initializeDatabase0.databaseURL);
        initializeDatabase0.createDatabase();

        InitializeDatabase initializeDatabase1 = new InitializeDatabase();
        initializeDatabase1.setDatabaseURL("Server_1");
        initializeDatabase1.startSQL(initializeDatabase1.JDBC_DRIVER, initializeDatabase1.databaseURL);
        initializeDatabase1.initial();

        InitializeDatabase initializeDatabase2 = new InitializeDatabase();
        initializeDatabase2.setDatabaseURL("Server_2");
        initializeDatabase2.startSQL(initializeDatabase2.JDBC_DRIVER, initializeDatabase2.databaseURL);
        initializeDatabase2.initial();


    }
}
