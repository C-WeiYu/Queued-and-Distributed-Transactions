import org.apache.zookeeper.*;

import java.io.IOException;

public class Init {
    public static void main (String[] args) throws InterruptedException, KeeperException {
        ZooKeeper client = null;
        try {
            client = new ZooKeeper("127.0.0.1:2181", 5000, new Watcher() {
                @Override
                public void process(WatchedEvent watchedEvent) {
                    if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
                    }
                }
            });
            System.out.println(client.getState());
        } catch (IOException e) {
        }

        if (client.exists("/App",false) != null){
            ZKUtil.deleteRecursive(client,"/App");
        }
        if (client.exists("/Locks",false) != null){
            ZKUtil.deleteRecursive(client,"/Locks");
        }

        String Data = "";
        client.create("/Locks",Data.getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        client.create("/App",Data.getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
//        for(int i = 1 ;i < 3;i++){
//            String Path = String.format("/Locks/Server_%d",i);
//            client.create(Path,Data.getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
//        }
//
//        String schema_Server_1 = "CREATE TABLE Server_1 (id int auto_increment primary key,name nvarchar,number int)";
//        String index = schema_Server_1.split("[(]")[1].split("[)]")[0];
//        System.out.println(index);
//        for (String value:index.split("[,]")){
//            String data = "";
//            String path = "/Locks/Server_1/"+value.split("[\s]")[0];
//            client.create(path,data.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
//            for(int i = 0 ; i < 10 ; i++){
//                String path1 = "/Locks/Server_1/"+value.split("[\s]")[0]+ "/" + i ;
//                client.create(path1,data.getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
//            }
//
//            System.out.println(value.split("[\s]")[0]);
//        }
//
//        String schema_Server_2 = "CREATE TABLE Server_2 (id int auto_increment primary key,name nvarchar,age int)";
//        String index_2 = schema_Server_2.split("[(]")[1].split("[)]")[0];
//        System.out.println(index_2);
//        for (String value:index_2.split("[,]")){
//            String data = "";
//            String path = "/Locks/Server_2/"+value.split("[\s]")[0];
//            client.create(path,data.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
//            System.out.println(value.split("[\s]")[0]);
//            for(int i = 0 ; i < 10 ; i++){
//                String path2 = "/Locks/Server_2/"+value.split("[\s]")[0]+ "/" + i ;
//                client.create(path2,data.getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
//            }
//        }
//    while(true){}
//
    }
}
