import org.apache.zookeeper.*;

import java.io.IOException;

public class InitializeZookeeper {
    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        ZooKeeper client ;
        client = new ZooKeeper("127.0.0.1:2181", 5000, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
                    System.out.println("Zookeeper連線成功");
                }
            }
        });

        for(int i = 0 ; i<100 ; i++) {
            client.create("/Locks/Server_1/DB/Item/"+i,"".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            client.create("/Locks/Server_2/DB/Item/"+i,"".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }
}
