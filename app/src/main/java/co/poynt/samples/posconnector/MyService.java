package co.poynt.samples.posconnector;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;

import co.poynt.api.model.Order;
import co.poynt.os.model.Intents;
import co.poynt.os.model.Payment;
import co.poynt.samples.posconnector.R;


/**
 * Code uses exapmles from http://examples.javacodegeeks.com/android/core/socket-core/android-socket-example/
 */
public class MyService extends Service {
    private static final String TAG = "MyService";
    public static final int SERVERPORT = 60000;

    private ServerSocket serverSocket;

    /*

    SSLServerSocketFactory sslserversocketfactory =
                    (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket sslserversocket =
                    (SSLServerSocket) sslserversocketfactory.createServerSocket(9999);
            SSLSocket sslsocket = (SSLSocket) sslserversocket.accept();

            InputStream inputstream = sslsocket.getInputStream();
     */


    private Thread serverThread;

    private Payment paymentResult;
    private PaymentResultReceiver paymentResultReceiver;

    private Context context = this;

    public void onCreate(){
        Log.d(TAG, "MyService created");

        serverThread = new Thread(new ServerThread());
        serverThread.start();

        // register payment result receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("co.poynt.samples.posconnector.PAYMENT_COMPLETED");
        paymentResultReceiver = new PaymentResultReceiver();
        registerReceiver(paymentResultReceiver, intentFilter);
    }
    public void onDestroy(){
        super.onDestroy();
        unregisterReceiver(paymentResultReceiver);
    }

    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
    public int onStartCommand (Intent intent, int flags, int startId){
        startForeground(1, getNotification());
        return START_STICKY;
    }

    private Notification getNotification() {
        Notification notification = new Notification.Builder(getApplicationContext())
                .setContentTitle("POS Connector running...")
                .setSmallIcon(R.drawable.ic_pos)
                .build();
        return notification;
    }

    class PaymentResultReceiver extends BroadcastReceiver{

        public void onReceive(Context context, Intent intent) {
            paymentResult = intent.getParcelableExtra(Intents.INTENT_EXTRAS_PAYMENT);
        }
    }
    class ServerThread implements Runnable {
        public void run() {
            Socket socket = null;
            try {
                serverSocket = new ServerSocket(SERVERPORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = serverSocket.accept();
                    CommunicationThread commThread = new CommunicationThread(socket);
                    new Thread(commThread).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class CommunicationThread implements Runnable{
        private Socket socket;
        BufferedReader input;
        public CommunicationThread(Socket s){
            socket=s;

            try{
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run() {
            try{
                String inputString = input.readLine();
                //Type listType = new TypeToken<Order>(){}.getType();
                //Order order = gson.fromJson(input, listType);
//                Gson json = new Gson();
//                Order order = json.fromJson(inputString, Order.class);
                Gson gson = new Gson();
                Type typeRequest = new TypeToken<POSRequest>(){}.getType();
                POSRequest posRequest = gson.fromJson(inputString, typeRequest);


                Intent dummyActivityIntent = new Intent (context, DummyTransparentActivity.class);
                dummyActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                dummyActivityIntent.putExtra("request", posRequest);
                startActivity(dummyActivityIntent);
                while (paymentResult == null ){
                    try {
                        // sleep for 100ms at a time until we get a result from payment fragment
                        Thread.currentThread().sleep(100);
                    }catch (InterruptedException e){
                        e.printStackTrace();
                    }
                }


                Type typeResponse = new TypeToken<Payment>(){}.getType();
                String result = gson.toJson(paymentResult, typeResponse);
                socket.getOutputStream().write(result.getBytes());
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally{
                // set paymentResult back to null
                paymentResult = null;
            }
        }
    }

}

/*

{
      "items":[
         {
            "name":"Cross Calais Pen",
            "productId":"2101960323",
            "quantity":3,
            "tax":129,
            "unitPrice":1600
         }
      ],
      "createdAt":"2015-11-12T14:23:17+00:00",
      "updatedAt":"2015-11-12T22:23:18+00:00",
      "id":"da035be1-287e-4ae4-9440-ff30de034f89",
      "amounts":{
         "currency":"USD",
         "discountTotal":0,
         "subTotal":4800,
         "taxTotal":129
      },
      "statuses":[
         {
            "status":"CLOSED"
         }
      ],
      "notes":"%7B%22customerName%22%3A%22carol%22%2C%22imageUrl%22%3A%22http%3A%2F%2Fmoney2020.mybluemix.net%2Fimages%2Fselfies%2Fda035be1-287e-4ae4-9440-ff30de034f89.png%22%7D"
   }

 */