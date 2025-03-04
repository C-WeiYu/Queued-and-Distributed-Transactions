import caller.CallerImpl;
import server.Account;
import server.Bank;
import server.Teller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Experient {
    public static void main(String[] args) throws InterruptedException {
        for(int i = 2 ; i<=3 ; i++){
            Thread.sleep(100);
            Account account = new Account("AccountService","AccountService"+i);
            Teller teller = new Teller("TellerService","TellerService"+i);
//            Bank bank = new Bank("BankService","BankService"+i);
            Thread accountThread = new Thread(account);
            Thread tellerThread = new Thread(teller);
//            Thread bankThread = new Thread(bank);
            accountThread.start();
            tellerThread.start();
//            bankThread.start();
        }
        Thread.sleep(1500);

        for(int k = 2 ; k<=3 ; k++){
//            int i = 2;
            if (k % 2 == 0 ){
//                CallerImpl caller = new CallerImpl(3,"AccountService:Account_Record/Balance/1=1,TellerService:Teller_Record/Balance/1=1,BankService:Bank_Record/Balance/1=1" , "Write","AccountService"+k,"TellerService"+k,"BankService"+k);
                CallerImpl caller = new CallerImpl(2,"AccountService:Account_Record/Balance/1=1,TellerService:Teller_Record/Balance/1=1" , "Write","AccountService"+k,"TellerService"+k,null);

                Thread callerThread = new Thread(caller);
                callerThread.start();
//                callerThread.join();
            }

            else {
//                CallerImpl caller = new CallerImpl(3,"AccountService:Account_Record/Balance/1,TellerService:Teller_Record/Balance/1,BankService:Bank_Record/Balance/1" , "Read","AccountService"+k,"TellerService"+k,"BankService"+k);
                CallerImpl caller = new CallerImpl(2,"AccountService:Account_Record/Balance/1,TellerService:Teller_Record/Balance/1" , "Read","AccountService"+k,"TellerService"+k,null);

                Thread callerThread = new Thread(caller);
                callerThread.start();
//                callerThread.join();
            }
        }
        Thread.sleep(1500);

    }
}
