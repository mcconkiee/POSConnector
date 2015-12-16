package co.poynt.samples.posconnector;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.UUID;

import co.poynt.os.model.Intents;
import co.poynt.os.model.Payment;
import co.poynt.os.model.PaymentStatus;
import co.poynt.samples.posconnector.R;

public class DummyTransparentActivity extends Activity {
    private static final String TAG = "DummyActivity";
    private static final int COLLECT_PAYMENT_REQUEST = 13132;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dummy_transparent);
        POSRequest posRequest = getIntent().getParcelableExtra("request");
        launchPoyntPayment(posRequest.getPurchaseAmount());
    }

    private void launchPoyntPayment(Long amount) {
        Log.d(TAG, "entered launchPoyntPayment");
        String currencyCode = NumberFormat.getCurrencyInstance().getCurrency().getCurrencyCode();
        //Log.d("Wow", "after currencyCode" );
        Payment payment = new Payment();
        String referenceId = UUID.randomUUID().toString();
        payment.setReferenceId(referenceId);
        payment.setAmount(amount);
        payment.setCurrency(currencyCode);
        payment.setMultiTender(true);

        // Log.d("Wow", "before try{");
        // start Payment activity for result
        try {
            //Intent collectPaymentIntent = new Intent(Intents._COLLECT_PAYMENT);
            //Intent collectPaymentIntent = new Intent(Intents.ACTION_COLLECT_MULTI_TENDER_PAYMENT);
            //collectPaymentIntent.putExtra(Intents.INTENT_EXTRAS_PAYMENT, payment);

            String action = Intents.ACTION_COLLECT_PAYMENT;
            Intent collectPaymentIntent = new Intent(action);
            collectPaymentIntent.putExtra(Intents.INTENT_EXTRAS_PAYMENT, payment);

            startActivityForResult(collectPaymentIntent, COLLECT_PAYMENT_REQUEST);
            //Product product = new Product();
            //product.setName("Yippe");
            //product.setSku("My SKU");
            // CurrencyAmount amount = new CurrencyAmount();
            //payment.setAmount(111l);
            //payment.setCurrency("USD");
            //product.setPrice(111l);
            //Intent intent = new Intent(Intents.ACTION_ADD_PRODUCT_TO_CART);
            //intent.putExtra(Intents.INTENT_EXTRA_PRODUCT, product);
            //intent.putExtra(Intents.INTENT_EXTRA_QUANTITY, 2);
            //intent.putExtra(Intents.INTENT_EXTRA_TAX, 0);
            //this.sendBroadcast(intent);
            //Log.d("Wow", intent.toString());

        } catch (ActivityNotFoundException ex) {
            Log.e("Wow", "Poynt Payment Activity not found - did you install PoyntServices?", ex);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "Received onActivityResult (" + requestCode + ")");
        // Check which request we're responding to
        if (requestCode == COLLECT_PAYMENT_REQUEST) {
            // Make sure the request was successful
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    Payment payment = data.getParcelableExtra(Intents.INTENT_EXTRAS_PAYMENT);
                    Log.d(TAG, "Received onPaymentAction from PaymentFragment w/ Status("
                            + payment.getStatus() + ")");
                    if (payment.getStatus().equals(PaymentStatus.COMPLETED)) {
                        Toast.makeText(this, "Payment Completed", Toast.LENGTH_LONG).show();
                    } else if (payment.getStatus().equals(PaymentStatus.AUTHORIZED)) {
                        Toast.makeText(this, "Payment Authorized", Toast.LENGTH_LONG).show();
                    } else if (payment.getStatus().equals(PaymentStatus.CANCELED)) {
                        Toast.makeText(this, "Payment Canceled", Toast.LENGTH_LONG).show();
                    } else if (payment.getStatus().equals(PaymentStatus.FAILED)) {
                        Toast.makeText(this, "Payment Failed", Toast.LENGTH_LONG).show();
                    } else if (payment.getStatus().equals(PaymentStatus.REFUNDED)) {
                        Toast.makeText(this, "Payment Refunded", Toast.LENGTH_LONG).show();
                    } else if (payment.getStatus().equals(PaymentStatus.VOIDED)) {
                        Toast.makeText(this, "Payment Voided", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Payment Completed", Toast.LENGTH_LONG).show();
                    }
                    Intent paymentResultIntent = new Intent();
                    paymentResultIntent.setAction("co.poynt.samples.posconnector.PAYMENT_COMPLETED");
                    paymentResultIntent.putExtra(Intents.INTENT_EXTRAS_PAYMENT, payment);
                    sendBroadcast(paymentResultIntent);

                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Payment Canceled", Toast.LENGTH_LONG).show();
            }
        }

        finish();
    }
    public void onDestroy() {
        super.onDestroy();
        //android.os.Process.killProcess(android.os.Process.myPid());
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_dummy_transparent, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }
}
