# 2PC_Zookeeper
Two Phase Commit with MQTT and Zookeeper

### 在Locks機制，當兩個Caller分別以asynchronized方式排隊等待兩個相同Server的兩個Row時
- Server_1的Sequential Znode : 0000000000可能為Caller_1
- Server_2的Sequential Znode : 0000000000可能為Caller_2
