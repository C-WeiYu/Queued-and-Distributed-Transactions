package TEST;


import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.fusesource.mqtt.client.*;

import javax.swing.*;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Test2 {
    static Boolean test = true;
    public static void main(String[] args) throws Exception {
        ZooKeeper client ;
        client = new ZooKeeper("127.0.0.1:2181", 5000, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
                    System.out.println("Zookeeper連線成功");
                }
            }
        });

        System.out.println(client.exists("/test",false));
    }

}
