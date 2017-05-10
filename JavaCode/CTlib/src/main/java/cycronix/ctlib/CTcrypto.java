// encrypt/decrypt bytearray utility

package cycronix.ctlib;

import java.security.Key;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
 
/**
 * A utility class that encrypts or decrypts a byte array.
 * @author cycronix
 *
 */
public class CTcrypto {
	private static final String SALT = "CloudTurbine";
	private Key secretKey;

	// constructor:  create key by hashing password
	CTcrypto(String password) throws Exception {
		byte[] key = (SALT + password).getBytes("UTF-8");
		MessageDigest sha = MessageDigest.getInstance("SHA-1");
		key = sha.digest(key);
		key = Arrays.copyOf(key, 16); // use only first 128 bit
		secretKey = new SecretKeySpec(key, "AES");
	}

	// encrypt
	byte[] encrypt(byte[] src) throws Exception {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, secretKey);
		byte[] iv = cipher.getIV(); 
		assert iv.length == 12;
		byte[] cipherText = cipher.doFinal(src);
		assert cipherText.length == src.length + 16; 
		byte[] message = new byte[12 + cipherText.length]; 
		System.arraycopy(iv, 0, message, 0, 12);
		System.arraycopy(cipherText, 0, message, 12, cipherText.length);
		return message;
	}

	// decrypt
	byte[] decrypt(byte[] message) throws Exception {
		if (message.length < 12 + 16) throw new IllegalArgumentException();
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec params = new GCMParameterSpec(128, message, 0, 12);
		cipher.init(Cipher.DECRYPT_MODE, secretKey, params);
		return cipher.doFinal(message, 12, message.length - 12);
	}

}
