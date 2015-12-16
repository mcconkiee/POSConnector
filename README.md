The app has a sample self-signed cert used by the SSLServerSocket in assets/ServerKeystore.bks.

To generate your own keystore you will first need to download Bouncycastle jar and then generate the keystore using the following command:

keytool -genkey -keystore ServerKeystore.bks -storetype BKS -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath <path to jar>/bcprov-jdk15on-146.jar
