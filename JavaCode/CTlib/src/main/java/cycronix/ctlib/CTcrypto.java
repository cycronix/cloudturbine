// encrypt/decrypt bytearray utility

package cycronix.ctlib;

import java.security.Key;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
 
/**
 * A utility class that encrypts or decrypts a byte array.
 * @author cycronix
 *
 */
public class CTcrypto {
	private static final String ALGORITHM = "AES";
	private static final String TRANSFORMATION = "AES";
	private static final String SALT = "CloudTurbine";
	private Key secretKey;
	
	CTcrypto(String password) throws Exception {
		byte[] key = (SALT + password).getBytes("UTF-8");
		MessageDigest sha = MessageDigest.getInstance("SHA-1");
		key = sha.digest(key);
		key = Arrays.copyOf(key, 16); // use only first 128 bit
		secretKey = new SecretKeySpec(key, ALGORITHM);
	}
	
	private byte[] doCrypto(byte[] inputBytes, int cipherMode) throws Exception {
		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		cipher.init(cipherMode, secretKey);
		return cipher.doFinal(inputBytes);
	}

	public byte[] encrypt(byte[] inputBytes) throws Exception {
		return doCrypto(inputBytes, Cipher.ENCRYPT_MODE);
	}

	public byte[] decrypt(byte[] inputBytes) throws Exception {
		return doCrypto(inputBytes, Cipher.DECRYPT_MODE);
	}
}
