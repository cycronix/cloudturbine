
/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

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

// ref: http://stackoverflow.com/questions/31851612/java-aes-gcm-nopadding-what-is-cipher-getiv-giving-me

public class CTcrypto {
    // AES-GCM parameters
    public static final int AES_KEY_SIZE = 128; 	// in bits
    public static final int GCM_NONCE_LENGTH = 12; 	// in bytes
    public static final int GCM_TAG_LENGTH = 16; 	// in bytes

	private static final String SALT = "CloudTurbine";						// randomize?
	private Key secretKey;
	private boolean optionalDecrypt = false;
	
	// constructor:  create key by hashing password
	CTcrypto(String password, boolean optional) throws Exception {
		optionalDecrypt = optional;
		byte[] key = (SALT + password).getBytes("UTF-8");
		MessageDigest sha = MessageDigest.getInstance("SHA-1");
		key = sha.digest(key);
		key = Arrays.copyOf(key, AES_KEY_SIZE/8); 							// use only first 128 bit
		secretKey = new SecretKeySpec(key, "AES");
	}

	CTcrypto(String password) throws Exception {
		this(password, false);
	}
	
	// encrypt
	byte[] encrypt(byte[] src) throws Exception {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");			// GCM authenticates
		cipher.init(Cipher.ENCRYPT_MODE, secretKey);
		
		byte[] cipherText = cipher.doFinal(src);							// encrypt
		assert cipherText.length == src.length + GCM_TAG_LENGTH; 			// 16 = GCMParameterSpec(firstArgBits/8)
		byte[] message = new byte[GCM_NONCE_LENGTH + cipherText.length]; 	// make room for cipherText plus IV (nonce)
		
		byte[] iv = cipher.getIV(); 										// get built-in random IV
		assert iv.length == GCM_NONCE_LENGTH;								// 12 = general standard for GCM
		System.arraycopy(iv, 0, message, 0, GCM_NONCE_LENGTH);				// copy IV plus cypherText into result
		System.arraycopy(cipherText, 0, message, GCM_NONCE_LENGTH, cipherText.length);
		
		return message;
	}

	// decrypt
	byte[] decrypt(byte[] message) throws Exception {
		if (message.length < GCM_NONCE_LENGTH + GCM_TAG_LENGTH) 			// needs room for message + IV + authentication tag
			throw new IllegalArgumentException();

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");	
		GCMParameterSpec params = new GCMParameterSpec(8*GCM_TAG_LENGTH, message, 0, GCM_NONCE_LENGTH);

		cipher.init(Cipher.DECRYPT_MODE, secretKey, params);
		try {
			return cipher.doFinal(message, GCM_NONCE_LENGTH, message.length - GCM_NONCE_LENGTH);	// skip IV at start
		}
		catch(Exception e) {
			if(optionalDecrypt) {
				CTinfo.debugPrint("Warning: decrypt failed, returning raw data");
				return message;
			}
			else throw e;
		}
	}

}
