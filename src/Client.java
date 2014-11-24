import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.xml.bind.DatatypeConverter;

public class Client {


	public static void main(String[] args) throws IOException {
		
		// Initialize connection to server's socket
		String hostName = "127.0.0.1";
		int portNumber = 4000;

		// Initialize the IO from server
		try (Socket socket = new Socket(hostName, portNumber);
				PrintWriter clientOut = new PrintWriter(socket.getOutputStream(), true);
				BufferedReader clientIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));) {
			BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

			// Generate AES session key
			KeyGenerator keygen = KeyGenerator.getInstance("AES");
			SecretKey sessionKey = keygen.generateKey();

			// Initialize encryption and decryption ciphers using session key
			byte[] iv = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
			IvParameterSpec ips = new IvParameterSpec(iv);
			Cipher sessionEncryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			sessionEncryptCipher.init(Cipher.ENCRYPT_MODE, sessionKey, ips);
			Cipher sessionDecryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			sessionDecryptCipher.init(Cipher.DECRYPT_MODE, sessionKey, ips);

			String fromServer; // a message from the server
			String fromUser; // message to send server
			BigInteger modulus = new BigInteger("123"); // modulus of the RSA
														// public key
			BigInteger exponent = new BigInteger("123"); // Exponent of the RSA
															// public key

			// Receive initialization messages from server. Server sends
			// messages in the following format:
			//
			// "modulus:" + modulus
			// "exponent:" + exponent
			// "end key"
			// 
			// The code below gets and saves this data for rebuilding the RSA
			// public key
			while ((fromServer = clientIn.readLine()) != null) {
				if (fromServer.contains("modulus:")) {
					modulus = new BigInteger(fromServer.split(":")[1].trim());
				} else if (fromServer.contains("public exponent:")) {
					exponent = new BigInteger(fromServer.split(":")[1].trim());
				}
				if (fromServer.equals("end key"))
					break;
			}

			// Rebuild servers public RSA key from data received
			RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
			KeyFactory factory = KeyFactory.getInstance("RSA");
			PublicKey pub = factory.generatePublic(spec);

			// Encrypt the AES session key with the RSA public key
			Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			rsaCipher.init(Cipher.ENCRYPT_MODE, pub);
			// encrypt the session key with the RSA public key HERE
			byte[] encryptedSessionKey = rsaCipher.doFinal(sessionKey.getEncoded());

			String stringifiedEncryptedSessionKey = DatatypeConverter.printBase64Binary(encryptedSessionKey);

			// Send the server the encrypted session key in the format:
			//
			// encryptedSessionKey
			// "end key"
			//
			clientOut.println(stringifiedEncryptedSessionKey);
			clientOut.println("end key");

			// Now read input from user and server.
			// With server input, messages must be decrypted using the AES
			// session key, and displayed to user
			// With user input, encrypt the messages with the AES session key,
			// display the encrypted data to user, and send to server
		    MessageDigest md = MessageDigest.getInstance("SHA-256");
			while (true) {
				
				// Server input to client, to be displayed to user
				if (clientIn.ready()) {
					fromServer = clientIn.readLine();
					String hash = clientIn.readLine();
					System.out.println("Received Encrypted Message:" + fromServer);
					System.out.println("hash:" + hash);

					// Decrypting message
					byte[] inputInByteArray = DatatypeConverter.parseBase64Binary(fromServer);
					byte[] decryptedInput = sessionDecryptCipher.doFinal(inputInByteArray);
					String decryptedMessage = new String(decryptedInput);
					
					// Rebuilding hash
					byte[] rebuiltHash = new byte[decryptedInput.length + sessionKey.getEncoded().length];
					System.arraycopy(decryptedInput, 0, rebuiltHash, 0, decryptedInput.length);
					System.arraycopy(sessionKey.getEncoded(), 0, rebuiltHash, decryptedInput.length, sessionKey.getEncoded().length);

				    byte outputHash[] = md.digest(rebuiltHash);
					
				    // Validating hash
					if (Arrays.equals(hash.getBytes(), outputHash)){
						System.out.println("Hash match!");
					} else {
						System.out.println("Hashes do not match! Decrypted message may be garbled and/or tempered with.");
					}
					
					// Displaying message
					System.out.println("Decrypted:" + decryptedMessage);
					
					if (decryptedMessage.equalsIgnoreCase("Bye."))
						break;
				}
				
				// User input to client, to be sent to server
				if (stdIn.ready()) {
					fromUser = stdIn.readLine();

					// Encrypting message
					byte[] inputInByteArray = fromUser.getBytes();
					byte[] encryptedInput = sessionEncryptCipher.doFinal(inputInByteArray);

					String encryptedMessage = DatatypeConverter.printBase64Binary(encryptedInput);
					
					// Building hash
					byte[] messageHash = new byte[inputInByteArray.length + sessionKey.getEncoded().length];
					System.arraycopy(inputInByteArray, 0, messageHash, 0, inputInByteArray.length);
					System.arraycopy(sessionKey.getEncoded(), 0, messageHash, inputInByteArray.length, sessionKey.getEncoded().length);
					
					byte outputHash[] = md.digest(messageHash);

					// Sending and displaying message
					System.out.println("Encrypted Message Sent: " + encryptedMessage);
					clientOut.println(encryptedMessage);
					System.out.println("hash:" + new String(outputHash));
					clientOut.println(new String(outputHash));
					if (fromUser.equalsIgnoreCase("Bye."))
						break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}