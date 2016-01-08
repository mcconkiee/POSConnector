package co.poynt.samples.posconnector;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.security.KeyStore;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import co.poynt.api.model.Order;
import co.poynt.os.model.Intents;
import co.poynt.os.model.Payment;
import co.poynt.os.model.PaymentStatus;
import co.poynt.os.model.PoyntError;
import co.poynt.os.services.v1.IPoyntOrderService;
import co.poynt.os.services.v1.IPoyntOrderServiceListener;

/**
 * Code uses examples from http://examples.javacodegeeks.com/android/core/socket-core/android-socket-example/
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

    private IPoyntOrderService mOrderService;
    /**
     * Class for interacting with the OrderService
     */
    private ServiceConnection mOrderServiceConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "PoyntOrderService is now connected");
            // this gets an instance of the IRemoteInterface, which we can use to call on the service
            mOrderService = IPoyntOrderService.Stub.asInterface(service);
        }
        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "PoyntOrderService has unexpectedly disconnected");
            mOrderService = null;
        }
    };

    private IPoyntOrderServiceListener saveOrderCallback = new IPoyntOrderServiceListener.Stub() {
        public void orderResponse(Order order, String s, PoyntError poyntError) throws RemoteException {
            Log.d("orderListener", "poyntError: " + (poyntError == null ? "" : poyntError.toString()));
        }
    };
    private class SaveOrderTask extends AsyncTask<Order, Void, Void>{
        protected Void doInBackground(Order... params) {
            Order order = params[0];
            String requestId = UUID.randomUUID().toString();
            if (mOrderService != null) {
                try {
                    mOrderService.createOrder(order, requestId, saveOrderCallback);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    public void onCreate(){
        Log.d(TAG, "MyService created");

        bindService(new Intent(IPoyntOrderService.class.getName()), mOrderServiceConnection, Context.BIND_AUTO_CREATE);

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
        unbindService(mOrderServiceConnection);
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
        public void run() {
            try{
                String inputString = input.readLine();

                Gson gson = new Gson();

                Type typeRequestResponse = new TypeToken<Payment>(){}.getType();
                Payment posPaymentRequest = gson.fromJson(inputString, typeRequestResponse);

                Intent dummyActivityIntent = new Intent (context, DummyTransparentActivity.class);
                // Intent.FLAG_ACTIVITY_NEW_TASK is needed because it's a new activity launched from service
                dummyActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                dummyActivityIntent.putExtra("request", posPaymentRequest);
                startActivity(dummyActivityIntent);
                synchronized (LOCK) {
                    while (paymentResult == null) {
                        try {
                            // wait until the payment result receiver gets notified that payment completed
                            LOCK.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                // if payment is canceled we need to set the original referenceId
                if (paymentResult.getStatus().equals(PaymentStatus.CANCELED)){
                    // when payment is canceled the response from Payment Fragment does not have reference id
                    // therefore setting it using reference id from the request
                    paymentResult.setReferenceId(posPaymentRequest.getReferenceId());
                }else{
                    // this means we had a successful payment and can save order
                    if (paymentResult.getOrder() != null) {
                        new SaveOrderTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, paymentResult.getOrder());
                    }
                }
                String result = gson.toJson(paymentResult, typeRequestResponse);
                socket.getOutputStream().write(result.getBytes());
            } catch (Exception e){
                Handler handler = new Handler(Looper.getMainLooper());
                final String errorMessage = e.getMessage();
                handler.post(new Runnable(){
                    public void run() {
                        Toast.makeText(MyService.this, "Exception: " + errorMessage + ". Check the logs", Toast.LENGTH_SHORT).show();
                    }
                });
                e.printStackTrace();
            } finally{
                // set paymentResult back to null
                paymentResult = null;
                try {
                    input.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
