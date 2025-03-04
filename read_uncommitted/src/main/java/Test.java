import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.fusesource.mqtt.client.*;

import java.net.URISyntaxException;
import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Test {
    static Boolean test = true;
    public static void main(String[] args) throws Exception {

//
//        String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
//        String databaseURL = "jdbc:mysql://localhost:3306/tpca_uncommitted?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
//        Statement stmt;
//        Class.forName(JDBC_DRIVER);
//        Connection conn= DriverManager.getConnection(databaseURL,"root","nccutest");
//        stmt =conn.createStatement();
//        String readSQL = String.format("Select * From Account_Record ");
//        ResultSet resultSet = stmt.executeQuery(readSQL);
//        while (resultSet.next()){
//            int finalInteger = resultSet.getInt("Balance");
//            System.out.println(finalInteger);
//
//        }
        //=============================================================================
        String IP = "127.0.0.1";
        int PORT_MQ = 1883;
        MQTT mqtt = new MQTT();
        mqtt.setHost(IP, PORT_MQ); // 設定ip和port
        BlockingConnection connection = mqtt.blockingConnection();
        connection.connect(); // 連接Broker
        Topic[] topics = { new Topic("Server_1111", QoS.EXACTLY_ONCE),new Topic("Server_12", QoS.EXACTLY_ONCE) };
        connection.subscribe(topics);
        connection.publish("Server_1111", "Hello World".getBytes(),QoS.EXACTLY_ONCE,false);
        String lastData = new String(connection.receive().getPayload());
        System.out.println(lastData);

        //=============================================================================

//        ZooKeeper client ;
//        client = new ZooKeeper("127.0.0.1:2181", 5000, new Watcher() {
//            @Override
//            public void process(WatchedEvent watchedEvent) {
//                if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
//                    System.out.println("Zookeeper連線成功");
//                }
//            }
//        });
//        Thread.sleep(5000);
//        client.create("/TEst","".getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
//        client.create("/TEst1","".getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);


        Thread.sleep(20000);

//        Thread.sleep(10000);

//


        }

    }
