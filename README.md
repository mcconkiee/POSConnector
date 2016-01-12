POSConnector is a sample app which facilitates communication between a POS app running on a tablet and a Poynt Smart Terminal over a TLS connection with client side certificate authentication.

To run a quick test:

- Install the app on a Poynt device
- Find IP address of your Poynt terminal using the following command: adb shell netcfg
- Download python client from https://github.com/dsnatochy/posconnectorpythonclient
- Run the client `python posconnectorclient.py`
- The Payment Fragment should come up on the terminal and after the payment is completed your terminal window should get a response in the form of a Payment json object.


JSON request sent by the client:
```
{
	"referenceId":"myRefId_12342",
	"amount": 1000,
	"tipAmount": 0,
	"currency":"USD",
	"disableTip": false,
	"authzOnly": false,
	"multiTender":true,
        "order":
	{
		"orderNumber": "123",
		"amounts": {
			"subTotal":2000,
			"discountTotal": -1200, 
			"taxTotal": 200,
			"netTotal": 1000,
			"currency": "USD"
		},

		"items": [
			{
				"sku": "12345",
				"unitPrice": 100,
				"tax": 100,
				"discounts": [{"amount":500,"customName":"$5 Discount"}],
				"quantity":10,
				"unitOfMeasure":"EACH",
				"clientNotes": "any special instructions from client",
				"status":"ORDERED",
				"name":"Mini scone"
			},
			{
				"sku": "54321",
				"unitPrice": 200,
				"tax": 100,
				"discounts": [{"amount":500,"customName":"$5 Discount"}],
				"quantity":5,
				"unitOfMeasure":"EACH",
				"clientNotes": "any special instructions from client",
				"status":"ORDERED",
				"name":"Coffee"
			}

		],
		"notes":"Note from the customer",
		"discounts": [
			{
				"amount":-200,
				"customName": "$2 Order Discount"
			}
		]
	}
}
```

The POSConnector has a sample self-signed cert used by the SSLServerSocket in keystore in assets/ServerKeystore.bks.

If you need to generate your own keystore you will first need to download Bouncycastle jar (https://www.bouncycastle.org/download/bcprov-jdk15on-146.jar) and then generate the keystore using the following command:

keytool -genkey -keystore ServerKeystore.bks -storetype BKS -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath <path to jar>/bcprov-jdk15on-146.jar

Your client will need to have the server certificate in its truststore. To extract server certificate from the server keystore you can run the following command:

keytool -exportcert -rfc -keystore <path>/ServerKeystore.bks -file <servercert>.pem -storetype BKS -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath <path>/bcprov-jdk15on-146.jar

To generate generate certificate and private key for the client:

openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 365

Client certificate need to be trusted by the server. To import the client certificate into server truststore:

keytool -import -trustcacerts -keystore <path>/assets/servertruststore.bks -storepass <pass> -noprompt -alias mycert -file <path>/cert.crt
