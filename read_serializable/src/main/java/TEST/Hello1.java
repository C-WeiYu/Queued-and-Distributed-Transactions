package TEST;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.fusesource.mqtt.client.*;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Hello1 {
    static Boolean test = true;
    public static void main(String[] args) throws Exception {
//        test = false;
//        String IP = "127.0.0.1";
//        int PORT_MQ = 1883;
//
//        Message message = null;
//
//        MQTT mqtt = new MQTT();
//        mqtt.setHost(IP, PORT_MQ); // 設定ip和port
//        BlockingConnection connection = mqtt.blockingConnection();
//        connection.connect(); // 連接Broker
//        System.out.println("Connected to Broker!");
//        //設置Topic，傳送品質為EXACTLY_ONCE
//        Topic[] topics = { new Topic("Server_1", QoS.EXACTLY_ONCE) };
//        connection.subscribe(topics);
//
//        while(true){
//            message = connection.receive(10, TimeUnit.SECONDS);
//            if(message != null){
//                byte[] MQTT_Item_B = message.getPayload();
//                String MQTT_Item_S = new String(MQTT_Item_B);
//                System.out.println("有收到訊息");
//                System.out.println(" ");
//                System.out.println("==========MQTT==========");
//                System.out.println("Received messages. " + new String(message.getPayload()));
//            }
//        }

        //=============================================================================

        ZooKeeper client ;
        client = new ZooKeeper("127.0.0.1:2181", 5000, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
                    System.out.println("Zookeeper連線成功");
                }
            }
        });

        client.create("/test/","test".getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);


//        for (int i = 0; i<10;i++){
//            String Path = String.format("/test/Caller_%d",i);
//            client.create(Path,"test".getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
//            List<String> list =client.getChildren("/test",false);
//            System.out.println(list.toString());
//        }

//        while(true){
//            Thread.sleep(3000);
//            List<String> list =client.getChildren("/test",false);
//            String min =Collections.min(list);
//            System.out.println(min);
//            String Path = String.format("/test/%s",min);
//            byte[] data = client.getData(Path,false,null);
//            String Data = new String(data);
//            System.out.println("Data : " + Data);
//        }
        //=============================================================================

//        HashMap<String, String> Status = new HashMap<String, String>();
//        Status.put("test1","Line");
//        Status.put("test2","Key");

//        while (Status.containsValue("Line")){
//            System.out.println("第一次");
//            Status.replace("test1","Key");
//            System.out.println(Status.toString());
//        }

//        for ( String Item:Status.keySet()){
//            if(Item.equals("test2")){
//                System.out.println(Status.get(Item));
//            }
//        }


//        if(list.toArray()[0].equals(1)){
//            System.out.println(list.toArray()[0]);
//        }
//        for(String value:list){
//            System.out.println(value);
//        }

        //=============================================================================
//        String Message = "Server_1:2,Server_2:3";
//        String[] Server_Row = Message.split(",");
//        //[S1:2,S2:3]
//        for(String value1:Server_Row){
//            String Lock_Server = value1.split(":")[0];
//            String Lock_Row = value1.split(":")[1];
//            System.out.println(Lock_Server);
//            System.out.println(Lock_Row);
//            String Lock_Path = String.format("/Locks/%s/%s",Lock_Server,Lock_Row);
//            System.out.println(Lock_Path);
//            Stat stat = client.exists(Lock_Path,false);
//            if (stat != null){
//                System.exit(0);
//            }
//            else {
//                client.create(Lock_Path,"Lock".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,CreateMode.EPHEMERAL);
//            }
//        }
//        while(true){
//            System.out.println("LOCKKKKKK");
//        }

    }

}
