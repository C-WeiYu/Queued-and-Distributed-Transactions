package caller;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.fusesource.mqtt.client.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class CallerImpl implements Runnable{
    private ZooKeeper zk ;

    private String TransactionIndex ;
    private BlockingConnection blockingConnection;
    private int serverNumber; //設定總Server數量
    private String initialMessage; //Server_1:Tabel/Column/Row=Num,Server_2:Tabel/Column/Row=Num

    private String type; //Read or Write
    private HashMap<String,String> firstPhase = new HashMap<String,String>();
    private int number = 0; //確認是否所有server都已經回覆"Prepare / Abort"

    private List<Op> transactionListWithGetLock = new ArrayList<Op>(); //存放Zookeeper Transaction的Ops

    private List<Integer> transactionListIndexWithGetLockx = new ArrayList<Integer> (); //存放Zookeeper Transaction Ops對應的requestIndex

    private List<Op> transactionListAll = new ArrayList<Op>(); //存放Zookeeper Transaction的Ops
    private List<Op> transactionListWithoutGetLock = new ArrayList<Op>();

    private String txIndex;
    private List<String> servers = new ArrayList<String>(); //{"Server_1","Server_2"}
    private List<String> lockPaths = new ArrayList<String>();   //{"Table/Column/Row","Table/Column/Row"}
    private List<Integer> data = new ArrayList<Integer>();  //{"1","2"}

    private long startTime;
    private long finishTime;

    private long duringTime;

    private String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";

    private String databaseURL = "jdbc:mysql://localhost:3306/tpca_repeatable?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private int dataFromAccount;

    private int dataFromTeller;

    private int dataFromBank;

    private String accountService;

    private String tellerService;

    private String bankService;
    private Statement stmt;

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
//                    System.out.println("Zookeeper連線成功");
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

    void setDuringTime(){
        this.duringTime = this.finishTime-this.startTime;
    }

    void writeDataIntoDB() throws SQLException {
//        String operationTimeSQL = String.format("INSERT INTO TxLogs (Transaction ,AccountData , TellerData, BankData, Type) VALUES (\"%s\",%d ,%d, %d, \"%s\")",txIndex,dataFromAccount,dataFromTeller, dataFromBank,type);
        String operationTimeSQL = String.format("INSERT INTO TxLogs (Transaction ,AccountData , TellerData, BankData, StartTime, FinishTime, DuringTime, Type) VALUES (\"%s\",%d ,%d, %d, %d, %d, %d, \"%s\")",txIndex,dataFromAccount,dataFromTeller, dataFromBank, startTime, finishTime, (finishTime-startTime), type);
        stmt.executeUpdate(operationTimeSQL);
    }


    void createTxNode(){
        zk.create("/Transaction/Tx_", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL, new AsyncCallback.StringCallback() {
            @Override
            public void processResult(int i, String path, Object o, String name) {
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        createTxNode();
                        break;
                    case OK :
                        txIndex = name.split("/")[2]; //txIndex : Tx_0000000001
                        TransactionIndex = txIndex;
                        addWatch2PC(name);
                        break;
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
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        addWatch2PC(path);
                        break;

                    case OK:
                        try {
                            subscribeMQTT(txIndex);
                            //這裡加入判斷需不需要get Locks
                            for (int index = 0; index <serverNumber ; index++){
                                if(data.get(index) != 0 ){ //Index代表第幾個request
                                    transactionListIndexWithGetLockx.add(index);
                                    //171行有改
                                    transactionListWithGetLock.add(Op.create("/Data/"+servers.get(index)+"/"+lockPaths.get(index) +"/Write/" ,TransactionIndex.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL));
                                }
                                else{
                                    checkWhetherRead(index);
                                }
                            }
                            transactionListAll.addAll(transactionListWithGetLock);
                            transactionListAll.addAll(transactionListWithoutGetLock);
//                            System.out.println("Caller : 進入 getLocks");
                            getLocks(transactionListAll);
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
    void checkWhetherRead(int requestNumber) throws Exception {
        //194行有改
        if (zk.exists("/Data/" + servers.get(requestNumber) + "/" + lockPaths.get(requestNumber) + "/Read",false)==null){ //Read Root does not exist
            List<String> list =zk.getChildren("/Data/"+servers.get(requestNumber)+"/"+lockPaths.get(requestNumber)+"/Write",false);
            String readRootData ;
            if(list.size() != 0) {
                //0409 - 以下到else前改掉，變成create一個Node在Write Queue再addWatch在Index-1的Node，後面要補上setData與publishMQTT
                readRootData = "-1"; //待確認
            }
            else {
                readRootData = "0000000000";
                publishMQTT(requestNumber);
                System.out.println("發送讀取的Read Root Request");
            }
            // 0409 - 當Read Root不存在時，leader新增Read Root的同時也在Write Queue中新增一個節點卡位
            transactionListIndexWithGetLockx.add(requestNumber);
            transactionListWithGetLock.add(Op.create("/Data/"+servers.get(requestNumber)+"/"+lockPaths.get(requestNumber) +"/Write/" ,TransactionIndex.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL));
            transactionListWithoutGetLock.add(Op.create("/Data/"+servers.get(requestNumber)+"/"+lockPaths.get(requestNumber)+"/Read",readRootData.getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE,CreateMode.PERSISTENT));
            transactionListWithoutGetLock.add(Op.create("/Data/"+servers.get(requestNumber)+"/"+lockPaths.get(requestNumber)+"/Read/",TransactionIndex.getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE,CreateMode.EPHEMERAL_SEQUENTIAL));

        }
        else { //Read Root exist
            transactionListWithoutGetLock.add(Op.create("/Data/"+servers.get(requestNumber)+"/"+lockPaths.get(requestNumber)+"/Read/",TransactionIndex.getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE,CreateMode.EPHEMERAL_SEQUENTIAL));
            //0409 - getReadRootData中新增，確認Read Root Data Changed後的值是變多少，如果不是0000000000則沒事
            getReadRootData(requestNumber);
        }
    }


    void getLocks(List<Op> Ops){
        zk.multi(Ops, new AsyncCallback.MultiCallback() {
            @Override
            public void processResult(int i, String s, Object o, List<OpResult> list){
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        getLocks(Ops);
                        break;

                    case OK:
                        for (Integer index = 0; index < transactionListWithGetLock.size() ; index++){
                            OpResult.CreateResult createResult = (OpResult.CreateResult) list.get(index);
                            if(data.get(transactionListIndexWithGetLockx.get(index)) == 0){
                                String selfIndex = createResult.getPath().split("/")[7];
                                try {
                                    zk.setData("/Data/"+servers.get(transactionListIndexWithGetLockx.get(index))+"/"+lockPaths.get(transactionListIndexWithGetLockx.get(index))+"/Read",selfIndex.getBytes(),-1);
                                } catch (KeeperException e) {
                                    throw new RuntimeException(e);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            waitLocks(transactionListIndexWithGetLockx.get(index), createResult.getPath());
                        }
                        break;

                    case NODEEXISTS:
                        transactionListAll.clear();
                        transactionListWithGetLock.clear();
                        transactionListIndexWithGetLockx.clear();
                        transactionListWithoutGetLock.clear();
                        for (int index = 0; index <serverNumber ; index++){
                            if(data.get(index) != 0 ){
                                transactionListIndexWithGetLockx.add(index);
                                transactionListWithGetLock.add(Op.create("/Data/"+servers.get(index)+"/"+lockPaths.get(index) +"/Write/" ,new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL));
                            }
                            else{
                                try {
                                    checkWhetherRead(index);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                        transactionListAll.addAll(transactionListWithGetLock);
                        transactionListAll.addAll(transactionListWithoutGetLock);
//                            System.out.println("Caller : 進入 getLocks");
                        getLocks(transactionListAll);
//
//
//                        System.out.println("getLocks Error : Node Exist : " + type );
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

//        String serverMessage = txIndex + "," + lockPaths.get(requestNumber) + "," + data.get(requestNumber); //Message_Server = "Tx_0000000001,Table/Column/9,5"
//        blockingConnection.publish(servers.get(requestNumber), serverMessage.getBytes(), QoS.EXACTLY_ONCE, false);
    }

    void checkLocks(int requestNumber,String path,String selfTxIndex) throws InterruptedException, KeeperException { //確認是否排到隊 // path = /Locks/Server_1/DB/Item/5/0000000001-1
        zk.exists(path, new Watcher() {
                    @Override
                    public void process(WatchedEvent watchedEvent) {
                        if (watchedEvent.getType().equals(Event.EventType.NodeDeleted)) {
                            //0409 - 當Write Queue中的節點收到前一個節點刪除的Noitification時，判斷此節點是Write節點還是Read節點
                            if (data.get(requestNumber)==1){ //Write
                                getReadRootDataForWrite(requestNumber,selfTxIndex);
                            }
                            else { //Read
                                try {
                                    zk.setData("/Data/" + servers.get(requestNumber) + "/" + lockPaths.get(requestNumber)+"/Read", "0000000000".getBytes(), -1);
                                    publishMQTT(requestNumber);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }

                        }
                    }
                }
                , new AsyncCallback.StatCallback() {
                    @Override
                    public void processResult(int i, String s, Object o, Stat stat) { //s = /Lock/Server_1/DB/Item/5/Write/0000000001-1
                        switch (KeeperException.Code.get(i)) {
                            case CONNECTIONLOSS:
                                try {
                                    checkLocks(requestNumber, s, selfTxIndex);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } catch (KeeperException e) {
                                    e.printStackTrace();
                                }
                                break;

                            case OK:
                                break;

                            case NONODE:
                                String selfTxIndex = s.split("/")[7];
                                String watchPath = s.substring(0, s.length() - 10) + String.format("%010d", Integer.valueOf(selfTxIndex) - 1);
                                try {
                                    checkLocks(requestNumber, watchPath, selfTxIndex); // watchPath表示自己的前一個節點
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
                }, null);

    }

    void addWatchForRead(int requestNumber,String lastIndex){
        zk.addWatch("/Data/" + servers.get(requestNumber) + "/" + lockPaths.get(requestNumber) + "/Write/" + lastIndex, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if(watchedEvent.getType().equals(Event.EventType.NodeDeleted)){
                    try {
                        publishMQTT(requestNumber); //Prepare or Abort
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        },AddWatchMode.PERSISTENT_RECURSIVE,null,null);

    }

    void getReadRootDataForWrite(int requestNumber , String minIndex){
//        System.out.println("Caller : Write交易讀取Read Root Data");
        zk.getData("/Data/" + servers.get(requestNumber) + "/" + lockPaths.get(requestNumber) + "/Read", new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if (watchedEvent.getType().equals(Event.EventType.NodeDataChanged)){
                    getReadRootDataForWrite(requestNumber,minIndex);
                }
            }
        }, new AsyncCallback.DataCallback() {
            @Override
            public void processResult(int i, String s, Object o, byte[] bytes, Stat stat) {
//                System.out.println("getReadRootDataForWrite的結果是 : " + KeeperException.Code.get(i).toString());
                switch (KeeperException.Code.get(i)) {
                    case CONNECTIONLOSS:
                        getReadRootData(requestNumber);
                        break;

                    case OK:
                        if (minIndex.compareTo(new String(bytes)) <= 0) {
                            System.out.println("Caller : 此時Write Nodez發送request的Index是" + minIndex);
//                            System.out.println("Caller : Read Root此時的Index : " + new String(bytes));
//                            System.out.println("Caller : 是否要發Request ? " + minIndex + " : " + (minIndex.compareTo(new String(bytes))<=0));
                            try {
                                publishMQTT(requestNumber);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        } else {
//                            System.out.println("Caller : 刪掉路徑 ： " + s);
                            System.out.println("Caller : 此時Write Node要刪除Read Root的Index是" + minIndex);
                            deleteReadRoot(requestNumber, s);
                        }
                        break;

                    case NONODE:
                        try {
                            publishMQTT(requestNumber);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        break;

                    default:
                        System.out.println("getReadRootData Error");
                        break;
                }

            }
        }, null);

    }

    void deleteReadRoot(int requestNumber , String readRootPath) {
        zk.getChildren(readRootPath,null, new AsyncCallback.ChildrenCallback() {
            @Override
            public void processResult(int i, String s, Object o, List<String> list) {
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        deleteReadRoot(requestNumber,readRootPath);
                        break;

                    case OK :
                        if(list.size() == 0 ){
                            try {
//                                System.out.println("Caller : Read Queue中沒有Node，可以刪除Root");
                                zk.delete(s,-1);
                                publishMQTT(requestNumber);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            } catch (KeeperException e) {
                                throw new RuntimeException(e);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        else {
//                            deleteReadRoot(requestNumber,readRootPath);
                            Collections.sort(list);
                            System.out.println("addWatch的路徑 : " + s + "/" + list.get(list.size() - 1));
                            zk.exists(s + "/" + list.get(list.size() - 1), new Watcher() {
                                @Override
                                public void process(WatchedEvent watchedEvent) {
                                    if(watchedEvent.getType().equals(Event.EventType.NodeDeleted)) {
                                        deleteReadRoot(requestNumber, s);
                                        System.out.println("這裡的s代表 : " + s);
                                    }
                                }
                            },null,null); //486-494取代495-503
//                                zk.addWatch(s + "/" + list.get(list.size() - 1), new Watcher() { //這裡改exist確認節點是否還在並新增watcher，不在的話async尋找下一個
//                                    @Override
//                                    public void process(WatchedEvent watchedEvent) {
//                                        if(watchedEvent.getType().equals(Event.EventType.NodeDeleted)){
//                                            deleteReadRoot(requestNumber,s);
//                                            System.out.println("這裡的s代表 : "+s);
//                                        }
//                                    }
//                                },AddWatchMode.PERSISTENT_RECURSIVE);
                        }
                        break;
                }
            }
        }, null);
    }

//    void deleteReadRoot(int requestNumber , String readRootPath) {
//        zk.getChildren(readRootPath, new Watcher() {
//            @Override
//            public void process(WatchedEvent watchedEvent) {
//                if (watchedEvent.getType().equals(Event.EventType.NodeDeleted) || watchedEvent.getType().equals(Event.EventType.NodeDataChanged)) {
//                    try {
////                        System.out.println("Caller : 節點已被刪出，發出request");
//                        publishMQTT(requestNumber);
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    }
//                }
//            }
//        }, new AsyncCallback.ChildrenCallback() {
//            @Override
//            public void processResult(int i, String s, Object o, List<String> list) {
//                switch (KeeperException.Code.get(i)){
//                    case CONNECTIONLOSS :
//                        deleteReadRoot(requestNumber,readRootPath);
//                        break;
//
//                    case OK :
//                        if(list.size() == 0 ){
//                            try {
////                                System.out.println("Caller : Read Queue中沒有Node，可以刪除Root");
//                                zk.delete(s,-1);
//                            } catch (InterruptedException e) {
//                                throw new RuntimeException(e);
//                            } catch (KeeperException e) {
//                                throw new RuntimeException(e);
//                            } catch (Exception e) {
//                                throw new RuntimeException(e);
//                            }
//                        }
//                        else {
//                            deleteReadRoot(requestNumber,readRootPath); //只用這行或換下面527-541行
////                            Collections.sort(list);
////                            try {
////                                zk.addWatch(s + "/" + list.get(list.size() - 1), new Watcher() {
////                                    @Override
////                                    public void process(WatchedEvent watchedEvent) {
////                                        if(watchedEvent.getType().equals(Event.EventType.NodeDeleted)){
////                                            deleteReadRoot(requestNumber,s);
////                                        }
////                                    }
////                                },AddWatchMode.PERSISTENT_RECURSIVE);
////                            } catch (KeeperException e) {
////                                throw new RuntimeException(e);
////                            } catch (InterruptedException e) {
////                                throw new RuntimeException(e);
////                            }
//                        }
//                        break;
//                }
//            }
//        }, null);
//    }

    void getReadRootData(int requestNumber){
        zk.getData("/Data/" + servers.get(requestNumber) +"/"+ lockPaths.get(requestNumber)+"/Read", new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if(watchedEvent.getType().equals(Event.EventType.NodeDataChanged)){
                    try {
                        getReadRootData(requestNumber);
//                        publishMQTT(requestNumber);
//                        System.out.println("已經送出！！！");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }, new AsyncCallback.DataCallback() {
            @Override
            public void processResult(int i, String s, Object o, byte[] bytes, Stat stat) {
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        getReadRootData(requestNumber);
                        break;

                    case OK :
                        if (new String(bytes).equals("0000000000")){
                            try {
                                System.out.println("有發送出資料！");
                                publishMQTT(requestNumber);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        break;

                    default :
                        System.out.println("getReadRootData Error");
                        break;
                }

            }
        },null);
    }

    void waitLocksForRead(int requestNumber){
        zk.getChildren("/Data/"+servers.get(requestNumber)+"/"+lockPaths.get(requestNumber)+"/Write" , false, new AsyncCallback.ChildrenCallback() {
            @Override
            public void processResult(int i, String s, Object o, List<String> list) { //s = /Locks/Server_1/DB/Item/5
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        waitLocksForRead(requestNumber);
                        break;

                    case OK :

                        if( list.size() == 0 ){
                            try {
                                publishMQTT(requestNumber);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        else {
                            String lastIndex = list.get(0);
                            for(String index:list){
                                if (index.compareTo(lastIndex) > 0){
                                    lastIndex = index ;
                                }
                            }
                            addWatchForRead(requestNumber,lastIndex);
                        }
                        break;
                    default :
                        System.out.println("waitLocksForRead Error");
                        break;
                }
            }
        },null);
    }

    //確認是否需要排隊
    void waitLocks(int requestNumber , String lockPath){ // /Locks/Server_1/DB/Item/5/0000000001
//        System.out.println("Caller : 進入 waitLocks");
        zk.getChildren("/Data/"+servers.get(requestNumber)+"/"+lockPaths.get(requestNumber)+"/Write" , false, new AsyncCallback.ChildrenCallback() {
            @Override
            public void processResult(int i, String s, Object o, List<String> list) { //s = /Locks/Server_1/DB/Item/5
                switch (KeeperException.Code.get(i)){
                    case CONNECTIONLOSS :
                        waitLocks(requestNumber,s);

                    case OK :
                        String minIndex = list.get(0);
                        for(String index:list){
                            if (index.compareTo(minIndex) < 0){
                                minIndex = index ;
                            }
                        }
                        String selfTxIndex = o.toString().split("/")[7];
                        if(selfTxIndex.equals(minIndex)){
                            //0409 - 當Write Queue中的節點收到前一個節點刪除的Noitification時，判斷此節點是Write節點還是Read節點
                            if(data.get(requestNumber)==1){ //Write
                                getReadRootDataForWrite(requestNumber,selfTxIndex);
                            }
                            else {
                                try {
                                    zk.setData("/Data/" + servers.get(requestNumber) + "/" + lockPaths.get(requestNumber)+"/Read", "0000000000".getBytes(), -1);
                                    publishMQTT(requestNumber);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                        else {
//                            System.out.println("Caller : Write的Index不是Write Queue中最小，準備addWatch在前一個Node");
                            String watchPath = lockPath.substring(0,lockPath.length()-10)+String.format("%010d",Integer.valueOf(selfTxIndex)-1);
                            try {
                                checkLocks(requestNumber, watchPath, selfTxIndex); //addWatch在Index-1的Node
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (KeeperException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
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
        else {
//            System.out.println("Caller : Bank == Null");
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
    }
}
