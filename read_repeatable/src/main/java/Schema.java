import org.apache.zookeeper.*;

public class Schema {
//    CREATE TABLE mapdata (
//            id integer NOT NULL AUTO_INCREMENT,
//            mapid integer NOT NULL,
//            x integer NOT NULL,
//            y integer NOT NULL,
//            z integer NOT NULL,
//            value varchar(1),
//    UNIQUE KEY(id))
    public static void main(String[] args) throws Exception {
         String schema_test = "CREATE TABLE mapdata (id integer NOT NULL AUTO_INCREMENT,mapid integer NOT NULL,x integer NOT NULL,y integer NOT NULL,z integer NOT NULL,value varchar(1),UNIQUE KEY(id))";
//         String schema_test = args[0];

    ZooKeeper client ;
    client = new ZooKeeper("127.0.0.1:2181", 5000, new Watcher() {
        @Override
        public void process(WatchedEvent watchedEvent) {
            if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
                System.out.println("Zookeeper連線成功");
            }
        }
    });


    String index = schema_test.split("[(]")[1].split("[)]")[0];
    for (String value:index.split("[,]")){
        String data = "";
        String path = "/Test/"+value.split("[\s]")[0];
        client.create(path,data.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
       System.out.println(value.split("[\s]")[0]);
        }
    while(true){}
    }
}

