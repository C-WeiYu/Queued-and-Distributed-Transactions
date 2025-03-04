package server;

import org.apache.zookeeper.*;
import org.fusesource.mqtt.client.*;


import java.io.IOException;
import java.sql.*;

public class Account implements Runnable {
    private Message message = null;
    private ZooKeeper zk;
    private BlockingConnection blockingConnection;

    private Connection connection;
    private Statement stmt;
    private String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private String databaseURL = "jdbc:mysql://localhost:3306/tpca_serializable?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private String serverName;

    private ResultSet readResultSet;
    private String column;
    private String appPath;
    private String txIndex;
    private String lockPath;
    private int row;
    private Integer data;

    private String table;

    private Integer finalInteger;

    private String twoPhaseCommit = "Abort";

    private String MQTTTopic ;

    public Account(String serverName , String MQTTTopic) {
        this.serverName = serverName;
        this.MQTTTopic = MQTTTopic;
    }

    void startZK() throws IOException {
        zk = new ZooKeeper("127.0.0.1:2181", 5000, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
                    System.out.println("Zookeeper連線成功");
                }
            }
        });
    }


    void stopZK() throws InterruptedException {
        zk.close();
    }

    void startMQTT(String IP, int PORT) throws Exception {
        MQTT mqtt = new MQTT();
        mqtt.setHost(IP, PORT); // 設定ip和port
        blockingConnection = mqtt.blockingConnection();
        blockingConnection.connect(); // 連接Broker

    }

    void startSQL(String JDBC, String DB_URL) throws ClassNotFoundException, SQLException {
        Class.forName(JDBC);
        connection = DriverManager.getConnection(DB_URL,"root","nccutest");
        stmt =connection.createStatement();

    }

    void stopSQL() throws SQLException {
        connection.close();
    }


    void createLock(String server) {
        zk.create("/Data/" + server, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, new AsyncCallback.StringCallback() {
            @Override
            public void processResult(int i, String s, Object o, String s1) {

            }
        }, null);
    }


    void publishMQTT(String path, Integer ans) throws Exception {
        System.out.println(this.MQTTTopic + " : 有傳出 : " + path + " : " + ans);
        blockingConnection.publish(path+"_"+MQTTTopic, ans.toString().getBytes(), QoS.EXACTLY_ONCE, false);
    }

    void subscribeMQTT() throws Exception {
        Topic[] topics = { new Topic(MQTTTopic, QoS.EXACTLY_ONCE) };
        blockingConnection.subscribe(topics);
    }

    String getMessage() throws Exception {
        Message messageByte = blockingConnection.receive();
        String message = new String(messageByte.getPayload());
        messageByte.ack(); // 返回ack，告知Broker收到訊息
        return message;
    }

    void fixMessageToSQL(String Message) {  //Message= "Tx_0000000001,Table/Column/9,5"
        txIndex = Message.split(",")[0];
        String SQL_Info = Message.split(",")[1];
        data = Integer.valueOf(Message.split(",")[2]);
        System.out.println("SQL_Info = " + SQL_Info);
        table = SQL_Info.split("[/]")[0];
        column = SQL_Info.split("[/]")[1];
        row = Integer.parseInt(SQL_Info.split("[/]")[2]);
        appPath = "/Transaction" + "/" + txIndex + "/" + serverName; // /App/Tx_0000000001/Server_1用args換掉
        lockPath = "/Data/" + serverName + "/" + table + "/" + column + "/" + row;
        System.out.println("lockPath = " + lockPath);
//        sql = String.format("Select %s From %s where id = %s",column,table,row);
    }


    void createNode(String Path) {
        zk.create(Path, twoPhaseCommit.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL, new AsyncCallback.StringCallback() {
            @Override
            public void processResult(int i, String s, Object o, String s1) {
                System.out.println("Account : " + s1);
                System.out.println("Account : " +txIndex);

//                    addWatch(s1,items[3],searchSQL(items));
            }
        }, null);
    }

    void addWatch_Write(String Path, String txIndex) {
        zk.addWatch(Path, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                System.out.println("這裡 : " + watchedEvent.getType().toString());
                if (watchedEvent.getType().equals(Event.EventType.NodeDataChanged)) {
                    try {
                        String selectTxLogSQL = String.format("SELECT Transaction,Account,Money FROM Account_Logs WHERE Transaction = \"%s\"", txIndex);
                        ResultSet resultSet = stmt.executeQuery(selectTxLogSQL);
                        while (resultSet.next()) {
                            int item = resultSet.getInt("Account");
                            int num = resultSet.getInt("Money");
                            String updateDatabaseSQL = String.format("UPDATE Account_Record SET Balance = Balance + %d WHERE Account = %d", num, item);
                            stmt.execute(updateDatabaseSQL);
                            String readSQL = String.format("Select %s From %s where Account = %s", column, table, row);
                            try {
                                ResultSet resultSet_read = stmt.executeQuery(readSQL);
                                while (resultSet_read.next()) {
                                    finalInteger = resultSet_read.getInt("Balance");
                                }
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }

                            publishMQTT(txIndex, finalInteger);
                            stopZK();
                            stopSQL();
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }, AddWatchMode.PERSISTENT_RECURSIVE, new AsyncCallback.VoidCallback() {
            @Override
            public void processResult(int i, String s, Object o) {
                switch (KeeperException.Code.get(i)) {
                    case CONNECTIONLOSS:
                        addWatch_Write(s, txIndex);
                        break;

                    case OK:
                        String txLogSQL = String.format("INSERT INTO Account_Logs(Transaction,Account,Money) VALUES (\"%s\",%d,%d)", txIndex, row, data); // DB的item從0開始到99，row看需不需要-1
                        try {
                            stmt.execute(txLogSQL);
                            twoPhaseCommit = "Prepare";
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        createNode(s);

                        break;

                    default:
                        System.out.println("Error");
                }
            }
        }, null);
    }

    void addWatch_Read(String Path, String txIndex) { //After
        zk.addWatch(Path, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if (watchedEvent.getType().equals(Event.EventType.NodeDataChanged)) {
                    try {
                        String readSQL = String.format("Select %s From %s where Account = %s", column, table, row);
                        ResultSet resultSet = stmt.executeQuery(readSQL);
                        while (resultSet.next()) {
                            finalInteger = resultSet.getInt("Balance");
                            publishMQTT(txIndex, finalInteger);

                        }
                        stopZK();
                        stopSQL();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }, AddWatchMode.PERSISTENT_RECURSIVE, new AsyncCallback.VoidCallback() {
            @Override
            public void processResult(int i, String s, Object o) {
                switch (KeeperException.Code.get(i)) {
                    case CONNECTIONLOSS:
                        addWatch_Read(s, txIndex);
                        break;

                    case OK:
                        try {
                            String txLogSQL = String.format("INSERT INTO Account_Logs(Transaction,Account,Money) VALUES (\"%s\",%d,0)", txIndex, row); // DB的item從0開始到99，row看需不需要-1
                            stmt.execute(txLogSQL);
                            twoPhaseCommit = "Prepare";
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                        createNode(s);

                        break;

                    default:
                        System.out.println("Error");
                }
            }
        }, null);
    }
    @Override
    public void run() {
        try {
            startZK();
            startMQTT("127.0.0.1", 1883);
            startSQL(this.JDBC_DRIVER, this.databaseURL);
            subscribeMQTT();
            String message = getMessage();  //message = "Tx_0000000001,Table/Column/9"
            System.out.println(this.MQTTTopic + " : " + message);
            fixMessageToSQL(message);
            System.out.println("data : " + this.data);
            if (this.data == 0) {
                addWatch_Read(this.appPath, this.txIndex);
            } else {
                addWatch_Write(this.appPath, this.txIndex);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
