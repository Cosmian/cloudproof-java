package com.cosmian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;

import com.cosmian.jna.Ffi;
import com.cosmian.jna.abe.DecryptedHeader;
import com.cosmian.jna.abe.EncryptedHeader;
import com.cosmian.rest.abe.acccess_policy.Attr;
import com.cosmian.rest.kmip.objects.PrivateKey;
import com.cosmian.rest.kmip.objects.PublicKey;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestLocalABE_AES {

	private static final Ffi FFI = new Ffi();

	@BeforeAll
	public static void before_all() {
		TestUtils.initLogging();
	}

	@Test
	public void testError() throws Exception {
		Ffi ffi = new Ffi();
		assertEquals("", ffi.get_last_error());
		String error = "An Error éà";
		ffi.set_error(error);
		assertEquals("FFI error: " + error, ffi.get_last_error());
		String base = "0123456789";
		String s = "";
		for (int i = 0; i < 110; i++) {
			s += base;
		}
		assertEquals(1100, s.length());
		ffi.set_error(s);
		String err = ffi.get_last_error(1023);
		assertEquals(1023, err.length());
	}

	public byte[] hash(byte[] data) throws NoSuchAlgorithmException {
		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		byte[] passHash = sha256.digest(data);
		return passHash;
	}

	@Test
	public void testHybridCryptoSimple() throws Exception {

		System.out.println("");
		System.out.println("---------------------------------------");
		System.out.println(" Hybrid Crypto Test Simple");
		System.out.println("---------------------------------------");
		System.out.println("");

		// The data we want to encrypt/decrypt
		byte[] data = "This s a test message".getBytes(StandardCharsets.UTF_8);

		// A unique ID associated with this message. The unique id is used to
		// authenticate the message in the AES encryption scheme.
		// Typically this will be a hash of the content if it is unique, a unique
		// filename or a database unique key
		byte[] uid = MessageDigest.getInstance("SHA-256").digest(data);

		// Load an existing public key
		String publicKeyJson = Resources.load_resource("ffi/public_master_key.json");
		PublicKey publicKey = PublicKey.fromJson(publicKeyJson);

		/// The policy attributes that will be used to encrypt the content. They must
		/// exist in the policy associated with the Public Key
		Attr[] attributes = new Attr[] { new Attr("Department", "FIN"), new Attr("Security Level", "Confidential") };

		// Now generate the header which contains the ABE encryption of the randomly
		// generated AES key.
		// This example assumes that the Unique ID can be recovered at time of
		// decryption, and is thus not stored as part of the encrypted header.
		// If that is not the case check the other signature of #FFI.encryptedHeader()
		// to inject the unique id.
		EncryptedHeader encryptedHeader = FFI.encryptHeader(publicKey, attributes);

		// The data can now be encrypted with the generated key
		// The block number is also part of the authentication of the AES scheme
		byte[] encryptedBlock = FFI.encryptBlock(encryptedHeader.getSymmetricKey(), uid, 0, data);

		// Create a full message with header+encrypted data. The length of the header
		// is pre-pended.
		ByteBuffer headerSize = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(encryptedHeader.getEncryptedHeaderBytes().length);
		// Write the message
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		bao.write(headerSize.array());
		bao.write(encryptedHeader.getEncryptedHeaderBytes());
		bao.write(encryptedBlock);
		bao.flush();
		byte[] ciphertext = bao.toByteArray();

		//
		// Decryption
		//

		// Load an existing user decryption key
		String userDecryptionKeyJson = Resources.load_resource("ffi/fin_confidential_user_key.json");
		PrivateKey userDecryptionKey = PrivateKey.fromJson(userDecryptionKeyJson);

		// Parse the message by first recovering the header length
		int headerSize_ = ByteBuffer.wrap(ciphertext).order(ByteOrder.BIG_ENDIAN).getInt(0);
		// Then recover the encrypted header and encrypted content
		byte[] encryptedHeader_ = Arrays.copyOfRange(ciphertext, 4, 4 + headerSize_);
		byte[] encryptedContent = Arrays.copyOfRange(ciphertext, 4 + headerSize_, ciphertext.length);

		// Decrypt he header to recover the symmetric AES key
		DecryptedHeader decryptedHeader = FFI.decryptHeader(userDecryptionKey, encryptedHeader_);

		// decrypt the content, passing the unique id and block number
		byte[] data_ = FFI.decryptBlock(decryptedHeader.getSymmetricKey(), uid, 0, encryptedContent);

		// Verify everything is correct
		assertTrue(Arrays.equals(data, data_));
	}

	@Test
	public void testHybridCryptoWithMetaData() throws Exception {

		System.out.println("");
		System.out.println("---------------------------------------");
		System.out.println(" Hybrid Crypto Test With Meta Data");
		System.out.println("---------------------------------------");
		System.out.println("");

		String publicKeyJson = Resources.load_resource("ffi/public_master_key.json");
		PublicKey publicKey = PublicKey.fromJson(publicKeyJson);

		Attr[] attributes = new Attr[] { new Attr("Department", "FIN"), new Attr("Security Level", "Confidential") };
		byte[] uid = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		byte[] additionalData = new byte[] { 10, 11, 12, 13, 14 };
		EncryptedHeader encryptedHeader = FFI.encryptHeader(publicKey, attributes, Optional.of(uid),
				Optional.of(additionalData));

		System.out.println("Symmetric Key length " + encryptedHeader.getSymmetricKey().length);
		System.out.println("Encrypted Header length " + encryptedHeader.getEncryptedHeaderBytes().length);

		byte[] data = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		byte[] encryptedBlock = FFI.encryptBlock(encryptedHeader.getSymmetricKey(), uid, 0, data);
		System.out.println("Clear Text Length " + data.length);
		System.out.println("Symmetric Crypto Overhead " + FFI.symmetricEncryptionOverhead());
		System.out.println("Encrypted Block Length " + encryptedBlock.length);

		// Decryption
		String userDecryptionKeyJson = Resources.load_resource("ffi/fin_confidential_user_key.json");
		PrivateKey userDecryptionKey = PrivateKey.fromJson(userDecryptionKeyJson);

		DecryptedHeader header_ = FFI.decryptHeader(userDecryptionKey, encryptedHeader.getEncryptedHeaderBytes(),
				uid.length, additionalData.length);

		System.out.println("Decrypted Header: Symmetric Key Length " + header_.getSymmetricKey().length);
		System.out.println("Decrypted Header: UID Length " + header_.getUid().length);
		System.out.println("Decrypted Header: Additional Data Length " + header_.getAdditionalData());

		assertTrue(Arrays.equals(encryptedHeader.getSymmetricKey(), header_.getSymmetricKey()));
		assertTrue(Arrays.equals(uid, header_.getUid()));
		assertTrue(Arrays.equals(additionalData, header_.getAdditionalData()));

		byte[] data_ = FFI.decryptBlock(header_.getSymmetricKey(), header_.getUid(), 0, encryptedBlock);
		assertTrue(Arrays.equals(data, data_));

	}

	@Test
	public void testEmptyMetaData() throws Exception {

		System.out.println("");
		System.out.println("---------------------------------------");
		System.out.println(" Simple header tests");
		System.out.println("---------------------------------------");
		System.out.println("");

		String publicKeyJson = Resources.load_resource("ffi/public_master_key.json");
		PublicKey publicKey = PublicKey.fromJson(publicKeyJson);

		Attr[] attributes = new Attr[] { new Attr("Department", "FIN"), new Attr("Security Level", "Confidential") };
		EncryptedHeader encryptedHeader = FFI.encryptHeader(publicKey, attributes);

		System.out.println("Symmetric Key length " + encryptedHeader.getSymmetricKey().length);
		System.out.println("Encrypted Header length " + encryptedHeader.getEncryptedHeaderBytes().length);

		// Decryption
		String userDecryptionKeyJson = Resources.load_resource("ffi/fin_confidential_user_key.json");
		PrivateKey userDecryptionKey = PrivateKey.fromJson(userDecryptionKeyJson);

		DecryptedHeader header_ = FFI.decryptHeader(userDecryptionKey, encryptedHeader.getEncryptedHeaderBytes());

		System.out.println("Decrypted Header: Symmetric Key Length " + header_.getSymmetricKey().length);
		System.out.println("Decrypted Header: UID Length " + header_.getUid().length);
		System.out.println("Decrypted Header: Additional Data Length " + header_.getAdditionalData());

		assertTrue(Arrays.equals(encryptedHeader.getSymmetricKey(), header_.getSymmetricKey()));
		assertTrue(Arrays.equals(new byte[] {}, header_.getUid()));
		assertTrue(Arrays.equals(new byte[] {}, header_.getAdditionalData()));

	}

}
