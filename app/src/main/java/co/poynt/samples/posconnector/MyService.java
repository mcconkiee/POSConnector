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
import java.security.KeyStore;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

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

    private SSLServerSocket serverSocket;

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
            SSLSocket socket = null;
            String keyStoreFile = "ServerKeystore.bks";
            String keyStorePassword = "123456";

            try {

                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(getResources().getAssets().open(keyStoreFile), keyStorePassword.toCharArray());


                String keyalg = KeyManagerFactory.getDefaultAlgorithm();
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyalg);
                kmf.init(keyStore, keyStorePassword.toCharArray());

                SSLContext context = SSLContext.getInstance("TLS");
                context.init(kmf.getKeyManagers(), null, null);
                serverSocket = (SSLServerSocket)context.getServerSocketFactory().createServerSocket(SERVERPORT);

            } catch (Exception e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = (SSLSocket)serverSocket.accept();
                    CommunicationThread commThread = new CommunicationThread(socket);
                    new Thread(commThread).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class CommunicationThread implements Runnable{
        private SSLSocket socket;
        BufferedReader input;
        public CommunicationThread(SSLSocket s){
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
