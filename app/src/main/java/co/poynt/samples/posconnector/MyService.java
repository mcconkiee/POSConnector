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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

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
import co.poynt.os.model.PaymentStatus;
import co.poynt.samples.posconnector.R;


/**
 * Code uses exapmles from http://examples.javacodegeeks.com/android/core/socket-core/android-socket-example/
 */
public class MyService extends Service {
    private static final String TAG = "MyService";

    // port server will listen on for connections from a POS device
    public static final int SERVERPORT = 60000;

    private SSLServerSocket serverSocket;
    // to ensure only one request is in flight at a time
    ExecutorService fixedPool = Executors.newFixedThreadPool(1);
    private Thread serverThread;
    // payment status and details
    private Payment paymentResult;
    // broadcast received notified when payment is completed
    private PaymentResultReceiver paymentResultReceiver;

    private Context context = this;

    private Object LOCK = this;

    public void onCreate(){
        Log.d(TAG, "MyService created");

        serverThread = new Thread(new ServerThread());
        serverThread.start();

        // register payment result receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("co.poynt.samples.posconnector.PAYMENT_COMPLETED");
        intentFilter.addAction("co.poynt.samples.posconnector.PAYMENT_CANCELED");
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
            synchronized (LOCK) {
                LOCK.notify();
            }
        }
    }
    class ServerThread implements Runnable {
        public void run() {
            SSLSocket socket = null;
            String keyStoreFile = "ServerKeystore.bks";
            String keyStorePassword = "123456";

            String trustStoreFile = "cert/servertruststore.bks";
            String trustStorePassword = "123456";

            try {

                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(getResources().getAssets().open(keyStoreFile), keyStorePassword.toCharArray());

                // TrustStore
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(getResources().getAssets().open(trustStoreFile), trustStorePassword.toCharArray());

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);

                String keyalg = KeyManagerFactory.getDefaultAlgorithm();
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyalg);
                kmf.init(keyStore, keyStorePassword.toCharArray());

                SSLContext context = SSLContext.getInstance("TLS");
                //context.init(kmf.getKeyManagers(), null, null);
                context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
                serverSocket = (SSLServerSocket)context.getServerSocketFactory().createServerSocket(SERVERPORT);
                // to require client side cert
                serverSocket.setNeedClientAuth(true);

            } catch (Exception e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = (SSLSocket)serverSocket.accept();
                    CommunicationThread commThread = new CommunicationThread(socket);
                    fixedPool.execute(commThread);
//                    new Thread(commThread).start();
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
                // Intent.FLAG_ACTIVITY_NEW_TASK is needed because it's a new activity launched from service
                dummyActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                dummyActivityIntent.putExtra("request", posRequest);
                startActivity(dummyActivityIntent);
                synchronized (LOCK) {
                    while (paymentResult == null) {
                        try {
                            // sleep for 100ms at a time until we get a result from payment fragment
                            //Thread.currentThread().sleep(100);
                            LOCK.wait();
                            Log.d(TAG, "after wait()");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }


                Type typeResponse = new TypeToken<Payment>(){}.getType();
                // if payment is canceled we need to set the original referenceId
                if (paymentResult.getStatus().equals(PaymentStatus.CANCELED)){
                    paymentResult.setReferenceId(posRequest.getReferenceId());
                }
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
