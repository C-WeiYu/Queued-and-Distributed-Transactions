# Distributed-Queued-Transaction
以 Zookeeper 作為 Global Distributed Locking Service，實作 Queued Transaction的Queue機制，與Two Phase Commit中
 Controller 與 Cohorts 間的 Prepate Phase & Commit/Abort Phase 確認
 
 ### Two Phase Commit
---
 ### Queued Transaction
 ---
 ### Isolation Level
1. Read Uncommitted :
> * 當目前的資料欄位有交易正在讀取時，其他的交易可以對該欄位進行讀取、更新。
> * 目前的資料欄位有交易正在更新時，其他的交易可以對該欄位進行讀取，但無法進行更新。

2. Read Committed
> * 當目前的資料欄位有交易正在讀取時，其他的交易可以對該欄位進行讀取、更新。
> * 目前的資料欄位有交易正在更新時，其他的交易無法對該欄位進行讀取、更新。

3. Repeatable Read
> * 當目前的資料欄位有交易正在讀取時，其他的交易可以對該欄位進行讀取，但無法進行更新。
> * 目前的資料欄位有交易正在更新時，其他的交易無法對該欄位進行讀取、更新。

4. Serializable
> * 當目前的資料欄位有交易正在讀取時，其他的交易無法對該欄位進行讀取、更新。
> * 目前的資料欄位有交易正在更新時，其他的交易無法對該欄位進行讀取、更新。
