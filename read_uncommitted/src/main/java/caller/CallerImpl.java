package caller;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.fusesource.mqtt.client.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class CallerImpl implements Runnable {
    private ZooKeeper zk ;
    private BlockingConnection blockingConnection;
    private int serverNumber; //設定總Server數量
    private String initialMessage; //Server_1:Tabel/Column/Row=Num,Server_2:Tabel/Column/Row=Num

    private String type; //Read or Write

    private HashMap<String,String> firstPhase = new HashMap<String,String>();
    private int number = 0; //確認是否所有server都已經回覆"Prepare / Abort"

    private List<Op> transactionList = new ArrayList<Op>(); //存放Zookeeper Transaction的Ops

    private List<Integer> transactionListIndex = new ArrayList<Integer> (); //存放Zookeeper Transaction Ops對應的requestIndex
    private String txIndex;
    private List<String> servers = new ArrayList<String>(); //{"Server_1","Server_2"}
    private List<String> lockPaths = new ArrayList<String>();   //{"Table/Column/Row","Table/Column/Row"}
    private List<Integer> data = new ArrayList<Integer>();  //{"1","2"}

    private long startTime;
    private long finishTime;

    private long duringTime;

    private String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";

    private String databaseURL = "jdbc:mysql://localhost:3306/tpca_uncommitted?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private int dataFromAccount;

    private int dataFromTeller;

    private int dataFromBank;

    private String accountService;

    private String tellerService;

    private String bankService;

    private Statement stmt;
//    private long withoutConnecting;
public CallerImpl(int serverNumber, String initialMessage, String type, String accountService , String tellerService, String bankService){
        this.serverNumber = serverNumber;
        this.initialMessage = initialMessage;
        this.type = type;
        this.accountService = accountService;
        this.tellerService = tellerService;
        this.bankService = bankService;
    }

    void dealMessage(String message){
        String[] items = message.split(",");  //{Server_1:Tabel/Column/Row=Num,Server_2:Tabel/Column/Row=Num}
        for (String item:items ){
            String[] objects = item.split(":");
            servers.add(objects[0]); //{"Server_1","Server_2"}
            if (objects[1].contains("=")){
                String[] objects2 = objects[1].split("=");
                lockPaths.add(objects2[0]);             // {"Table/Column/Row","Table/Column/Row"}
                data.add(Integer.valueOf(objects2[1]));  // {"1","2"}
            }
            else {
                lockPaths.add(objects[1]);
                data.add(0);
            }
        }

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
    };

    void stopZK() throws InterruptedException {
        zk.close();
    }

    void startMQTT(String IP,int PORT) throws Exception {
        MQTT mqtt = new MQTT();
        mqtt.setHost(IP, PORT); // 設定ip和port
        blockingConnection = mqtt.blockingConnection();
        blockingConnection.connect(); // 連接Broker

    }

    void startSQL(String JDBC, String DB_URL) throws ClassNotFoundException, SQLException {
        Class.forName(JDBC);
        Connection conn= DriverManager.getConnection(DB_URL,"root","nccutest");
        stmt =conn.createStatement();

    }

    void setStartTime(){
        this.startTime = System.currentTimeMillis();
    }

    void setFinishTime(){
        this.finishTime = System.currentTimeMillis();
    }


    void writeDataIntoDB() throws SQLException {
//        String operationTimeSQL = String.format("INSERT INTO UncommittedDataBefore (Transaction ,Data1 , Data2 ,StartTime , FinishTime , DuringTime) VALUES (\"%s\",%d ,%d , %d , %d, %d)",txIndex,dataFromServer1,dataFromServer2,startTime,finishTime,duringTime);
        String operationTimeSQL = String.format("INSERT INTO TxLogs (Transaction ,AccountData , TellerData, BankData, StartTime, FinishTime, DuringTime, Type) VALUES (\"%s\",%d ,%d, %d, %d, %d, %d, \"%s\")",txIndex,dataFromAccount,dataFromTeller, dataFromBank, startTime, finishTime, (finishTime-startTime), type);
        stmt.executeUpdate(operationTimeSQL);
    }


    void createTxNode(){
        zk.create("/Transaction/Tx_", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL, new AsyncCallback.StringCallback() {
            @Override
            public void processResult(int i, String path, Object o, String name) {
//                System.out.println(KeeperException.Code.get(i).toString());
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        createTxNode();
                        break;
                    case OK :
                        txIndex = name.split("/")[2]; //txIndex : Tx_0000000001
                        //改
                        addWatch2PC(name);
                        break;
//                    case NODEEXISTS:
//                        System.out.println("ERROR");
//                        break;
                    default:
                        System.out.println("ERROR");
                        break;
                }
            }
        },null);
    }

    void addWatch2PC(String path){
        zk.addWatch(path, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if(watchedEvent.getType().equals(Event.EventType.NodeCreated)){
                    getData(watchedEvent.getPath()); //Prepare or Abort
                }
            }
        }, AddWatchMode.PERSISTENT_RECURSIVE, new AsyncCallback.VoidCallback() {
            @Override
            public void processResult(int i, String newName, Object o) {
//                System.out.println("addWatch2PC的Callback : " + KeeperException.Code.get(i));
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        addWatch2PC(path);
                        break;

                    case OK:
                        try {
                            subscribeMQTT(txIndex);
                            //這裡加入判斷需不需要get Locks
                            for (int index = 0; index <serverNumber ; index++){
                                if(data.get(index) != 0 ){
                                    transactionListIndex.add(index);
                                    transactionList.add(Op.create("/Data/"+servers.get(index)+"/"+lockPaths.get(index) +"/",new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL));
                                    // 改用transaction，再
//                                    getLocks(index);
                                }
                                else{
                                    publishMQTT(index);
                                }
                            }
                            if(transactionList.size() != 0 ){
                                getLocks(transactionList);
                            }
                            break;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    default:
                        System.out.println("addWatch2PC Error");
                        break;
                }
            }
        },null);
    }

    void getLocks(List<Op> Ops){
        zk.multi(Ops, new AsyncCallback.MultiCallback() {
            @Override
            public void processResult(int i, String s, Object o, List<OpResult> list) {
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        getLocks(Ops);
                        break;

                    case OK:
                        for (Integer index = 0; index < list.size() ; index++){
                            OpResult.CreateResult createResult = (OpResult.CreateResult) list.get(index);
                            waitLocks(transactionListIndex.get(index), createResult.getPath());
                        }
                        break;

                    default:
                        System.out.println("getLocks Error");
                        break;
                }
            }
        },null);
    }


    void publishMQTT(Integer requestNumber) throws Exception {       //info = {"Server_1","Table/Column/Row"}
        String serverMessage = txIndex + "," + lockPaths.get(requestNumber) + "," + data.get(requestNumber); //Message_Server = "Tx_0000000001,Table/Column/9,5"
        if(servers.get(requestNumber).toLowerCase().contains("account")){
            blockingConnection.publish(this.accountService, serverMessage.getBytes(),QoS.EXACTLY_ONCE,false);
            System.out.println("Caller : 發送訊息給 " + this.accountService + " ---> " + serverMessage.getBytes());
        }
        else if(servers.get(requestNumber).toLowerCase().contains("teller")){
            blockingConnection.publish(this.tellerService, serverMessage.getBytes(),QoS.EXACTLY_ONCE,false);
            System.out.println("Caller : 發送訊息給 " + this.tellerService + " ---> " + serverMessage.getBytes());

        }
        else if(servers.get(requestNumber).toLowerCase().contains("bank")){
            blockingConnection.publish(this.bankService, serverMessage.getBytes(),QoS.EXACTLY_ONCE,false);
            System.out.println("Caller : 發送訊息給 " + this.bankService + " ---> " + serverMessage.getBytes());

        }
//            System.out.println(servers.get(lockNumber));
    }

    void checkLocks(int requestNumber,String path) throws InterruptedException, KeeperException { //確認是否排到隊 // path = /Data/Server_1/DB/Item/5/0000000001-1
        zk.exists(path, false
        , new AsyncCallback.StatCallback() {
            @Override
            public void processResult(int i, String s, Object o, Stat stat) { //s = /Lock/Server_1/DB/Item/5/0000000001-1
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        try {
                            checkLocks(requestNumber,s);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (KeeperException e) {
                            e.printStackTrace();
                        }
                        break;

                    case OK :
//                        System.out.println("這裡是OK : " + s);
//                        System.out.println("checkLocks 成功");
                        try {
                            zk.exists(s, new Watcher() {
                                @Override
                                public void process(WatchedEvent watchedEvent) {
                                    if (watchedEvent.getType().equals(Event.EventType.NodeDeleted)) {
                                        try {
                                            publishMQTT(requestNumber);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            });
                        } catch (KeeperException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        break;

                    case NONODE:
//                        System.out.println("這裡是NONODE : " + s);
                        String selfTxIndex = s.split("/")[6];
                        String watchPath = s.substring(0,s.length()-10)+String.format("%010d",Integer.valueOf(selfTxIndex)-1);
                        try {
                            checkLocks(requestNumber,watchPath); // watchPath表示自己的前一個節點
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (KeeperException e) {
                            e.printStackTrace();
                        }
                        break;

                    default:
                        System.out.println("checkLocks Error");
                        break;
                }
            }
        },null);

    }



    //確認是否需要排隊
    void waitLocks(int requestNumber , String lockPath){ // /Data/Server_1/DB/Item/5/0000000001
        zk.getChildren("/Data/"+servers.get(requestNumber)+"/"+lockPaths.get(requestNumber) , false, new AsyncCallback.ChildrenCallback() {
            @Override
            public void processResult(int i, String s, Object o, List<String> list) { //s = /Data/Server_1/DB/Item/5
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        waitLocks(requestNumber,s);

                    case OK :
//                        System.out.println("路徑為 : " + s);
                        String minIndex = list.get(0);
//                        System.out.println("minIndex : " + minIndex);
                        for(String index:list){
                            if (index.compareTo(minIndex) < 0){
                                minIndex = index ;
                            }
                        }
                        String selfTxIndex = o.toString().split("/")[6];
                        if(selfTxIndex.equals(minIndex)){
                            try {
                                publishMQTT(requestNumber);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        else {
                            String watchPath = lockPath.substring(0,lockPath.length()-10)+String.format("%010d",Integer.valueOf(selfTxIndex)-1);
                            try {
                                checkLocks(requestNumber,watchPath);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (KeeperException e) {
                                e.printStackTrace();
                            }
                        }
                }
            }
        },lockPath);
    }

    void getData(String path){
        zk.getData(path, false, new AsyncCallback.DataCallback() {
            @Override
            public void processResult(int i, String s, Object o, byte[] bytes, Stat stat) {
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        getData(path);
                        break;

                    case OK:
                        String vote = new String(bytes);
                        firstPhase.put(s,vote);
                        if(getFirstPhase()==serverNumber ){
                            checkAbort(firstPhase);
                            String[] keySet = firstPhase.keySet().toArray(new String[0]);
                            setData(keySet,0);
                        }
                        break;

                    default:
                        System.out.println("getData Error");
                        break;
                }
            }
        },null);
    }

    void setData(String[] path,int num){
        zk.setData(path[num], "Commit".getBytes(), -1, new AsyncCallback.StatCallback() {
            @Override
            public void processResult(int i, String s, Object o, Stat stat) {
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        setData(path,num);
                        break;

                    case OK :
                        number += 1;
                        if (number < serverNumber){
                            setData(path,number);
                        }

                        break;

                    default :
                        System.out.println("setData Error");
                        break;
                }
            }
        },number);
    }

    void checkAbort(HashMap decision){
        if (decision.containsValue("Abort")){
            try {
                zk.close();
                System.exit(0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    void subscribeMQTT(String txIndex) throws Exception {
        if (!(this.accountService== null)){
            Topic[] topic = {new Topic(txIndex+"_"+this.accountService,QoS.EXACTLY_ONCE)};
            blockingConnection.subscribe(topic);
        }
        if (!(this.tellerService == null)){
            Topic[] topic = {new Topic(txIndex+"_"+this.tellerService,QoS.EXACTLY_ONCE)};
            blockingConnection.subscribe(topic);
        }

        if (!(this.bankService == null)){
            Topic[] topic = {new Topic(txIndex+"_"+this.bankService,QoS.EXACTLY_ONCE)};
            blockingConnection.subscribe(topic);
        }

    }





    void getMQTTData() throws Exception {
            for(int k = 0 ; k < serverNumber; k++){
                Message message = null;
                try {
                    message = blockingConnection.receive(); //Exception
                    String lastData = new String(message.getPayload());
                    System.out.println("-----取得資料 : " + lastData +" and " + "Topic : " + message.getTopic()+"-----");
                    // 把資料放入Tables;
                    if (message.getTopic().contains("AccountService")) {
                        dataFromAccount = Integer.valueOf(lastData);
                    } else if (message.getTopic().contains("TellerService")){
                        dataFromTeller = Integer.valueOf(lastData);
                    } else if (message.getTopic().contains("BankService")) {
                        dataFromBank = Integer.valueOf(lastData);
                    }

                    message.ack();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            blockingConnection.disconnect();
    }

    int getFirstPhase(){
        return firstPhase.size();
    }


    @Override
    public void run() {
        setStartTime();
        dealMessage(this.initialMessage);
        try {
            startZK();
            startMQTT("127.0.0.1",1883);
            startSQL(this.JDBC_DRIVER, this.databaseURL);
            createTxNode();
            getMQTTData();
            setFinishTime();
            writeDataIntoDB();
            stopZK();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
//        caller.setFinishTime();
//        caller.setDuringTime();

    }
}
