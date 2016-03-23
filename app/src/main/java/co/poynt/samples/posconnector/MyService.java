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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

//    private SSLServerSocket serverSocket;
    private ServerSocket serverSocket;
    // to ensure only one request is in flight at a time
    ExecutorService fixedPool = Executors.newFixedThreadPool(1);
    private Thread serverThread;
    // payment status and details
    private Payment paymentResult;
    // broadcast receiver notified when payment is completed
    private PaymentResultReceiver paymentResultReceiver;
    private Context context = this;
    private HashMap<String, BlockingQueue<Payment>> requestMap;
    //private BlockingQueue<Payment> LOCK;
    private IPoyntOrderService mOrderService;

    private final int REQUEST_TIMEOUT=60;
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

    private class SaveOrderTask extends AsyncTask<Order, Void, Void> {
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

    public void onCreate() {
        Log.d(TAG, "MyService created");
        requestMap = new HashMap<>();

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

    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(paymentResultReceiver);
        unbindService(mOrderServiceConnection);
    }

    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
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

    class PaymentResultReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            paymentResult = intent.getParcelableExtra(Intents.INTENT_EXTRAS_PAYMENT);
            try {
                BlockingQueue<Payment> LOCK = requestMap.get(paymentResult.getReferenceId());
                // LOCK will be null if request timed out and server closed connection
                if (LOCK != null){
                    LOCK.put(paymentResult);
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class ServerThread implements Runnable {
        public void run() {
            Socket socket = null;
            try {
                serverSocket = new ServerSocket(SERVERPORT);
            } catch (Exception e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = serverSocket.accept();
                    CommunicationThread commThread = new CommunicationThread(socket);
                    fixedPool.execute(commThread);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class CommunicationThread implements Runnable {
        private Socket socket;
        BufferedReader input;

        public CommunicationThread(Socket s) {
            socket = s;
            try {
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            String referenceId = null;
            try {

                String inputString = input.readLine();
                Log.d(TAG, "REQUEST: " + inputString);
                Gson gson = new Gson();
                Type typeRequestResponse = new TypeToken<Payment>() {}.getType();
                Payment posPaymentRequest = gson.fromJson(inputString, typeRequestResponse);

                referenceId = posPaymentRequest.getReferenceId();
                // if reference id is not passed, generate one
                if (referenceId == null || "".equals(referenceId)){
                    referenceId = UUID.randomUUID().toString();
                    posPaymentRequest.setReferenceId(referenceId);
                }

                BlockingQueue<Payment> LOCK = new ArrayBlockingQueue<Payment>(1);
                requestMap.put(posPaymentRequest.getReferenceId(), LOCK);

                Intent dummyActivityIntent = new Intent(context, DummyTransparentActivity.class);
                // Intent.FLAG_ACTIVITY_NEW_TASK is needed because it's a new activity launched from service
                dummyActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                dummyActivityIntent.putExtra("request", posPaymentRequest);
                startActivity(dummyActivityIntent);

                try {
                    // wait up to 60 seconds to get paymentResult from the payment fragment
                    paymentResult = LOCK.poll(REQUEST_TIMEOUT, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // clear the queue
                if (!LOCK.isEmpty()) LOCK.take();
                // this would happen if payment activity did not finish in 60 seconds
                if (paymentResult == null){
                    paymentResult = new Payment();
                    paymentResult.setStatus(PaymentStatus.FAILED);
                    paymentResult.setReferenceId(posPaymentRequest.getReferenceId());
                }
                // if payment is canceled or failed we need to set the original referenceId
                if (paymentResult.getStatus().equals(PaymentStatus.CANCELED) ||
                        paymentResult.getStatus().equals(PaymentStatus.FAILED)) {
                    // when payment is canceled the response from Payment Fragment does not have reference id
                    // therefore setting it using reference id from the request
                    paymentResult.setReferenceId(posPaymentRequest.getReferenceId());
                } else {
                    // this means we had a successful payment and can save order
                    if (paymentResult.getOrder() != null) {
                        new SaveOrderTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, paymentResult.getOrder());
                    }
                }
                String result = gson.toJson(paymentResult, typeRequestResponse);
                OutputStream os = socket.getOutputStream();
                os.write(result.getBytes());
                os.flush();
                //TODO should separate this into it's own try/catch to provide better messaging
                // 2nd write and flush will trigger exception if the client disconnected
                os.write("\n".getBytes());
                os.flush();
            } catch (Exception e) {
                Handler handler = new Handler(Looper.getMainLooper());
                final String errorMessage = e.getMessage();
                handler.post(new Runnable() {
                    public void run() {
                        Toast.makeText(MyService.this, "Exception: " + errorMessage + ". Check the logs", Toast.LENGTH_LONG).show();
                    }
                });
                e.printStackTrace();
            } finally {
                // set paymentResult back to null
                paymentResult = null;

                requestMap.remove(referenceId);

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
