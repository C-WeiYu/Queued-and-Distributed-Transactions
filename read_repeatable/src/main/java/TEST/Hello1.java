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
        String a ="0000000000";
        String b = "-1";
        System.out.println(a.compareTo(b));
    }
//        ZooKeeper client ;
//        client = new ZooKeeper("127.0.0.1:2181", 5000, new Watcher() {
//            @Override
//            public void process(WatchedEvent watchedEvent) {
//                if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
//                    System.out.println("Zookeeper連線成功");
//                }
//            }
//        });

}
