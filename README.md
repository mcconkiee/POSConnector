POSConnector is a sample app which facilitates communication between a POS app running on a tablet and a Poynt Smart Terminal over a TLS connection.

The app has a sample self-signed cert used by the SSLServerSocket in assets/ServerKeystore.bks.

To generate your own keystore you will first need to download Bouncycastle jar and then generate the keystore using the following command:

keytool -genkey -keystore ServerKeystore.bks -storetype BKS -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath <path to jar>/bcprov-jdk15on-146.jar

To run a quick test:

- Install the app on a Poynt device
- Find IP address of your Poynt terminal using the following command: adb shell netcfg
- Run the following OpenSSL command from terminal (assuming terminal IP is 192.168.1.2): openssl s_client -connect 192.168.1.2:60000
- Once the connection is established type a simple JSON request: {"action":"sale","purchaseAmount":1000,"currency":"USD","referenceId":"yourOrderId"} and press ENTER
- The Payment Fragment should come up on the terminal and after the payment is completed your terminal window should get a response in the form of a Payment object.
