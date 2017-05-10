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
 
public class CTcrypto {
	private static final String SALT = "CloudTurbine";		// randomize?
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
