package com.cosmian;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Hex;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cosmian.rest.abe.KmsClient;
import com.cosmian.rest.abe.access_policy.AccessPolicy;
import com.cosmian.rest.abe.access_policy.And;
import com.cosmian.rest.abe.access_policy.Attr;
import com.cosmian.rest.abe.access_policy.Or;
import com.cosmian.rest.abe.data.DecryptedData;
import com.cosmian.rest.abe.policy.Policy;
import com.cosmian.rest.kmip.data_structures.KeyValue;
import com.cosmian.rest.kmip.objects.PrivateKey;
import com.cosmian.rest.kmip.objects.PublicKey;
import com.cosmian.rest.kmip.types.Attributes;
import com.cosmian.rest.kmip.types.CryptographicAlgorithm;
import com.cosmian.rest.kmip.types.KeyFormatType;
import com.cosmian.rest.kmip.types.ObjectType;
import com.cosmian.rest.kmip.types.VendorAttribute;
import com.cosmian.utils.CloudproofException;
import com.cosmian.utils.Resources;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestKmsCoverCrypt {

    private static final Logger logger = Logger.getLogger(TestKmsCoverCrypt.class.getName());

    @BeforeAll
    public static void before_all() {
        TestUtils.initLogging();
    }

    private Policy policy() throws CloudproofException {
        return new Policy(20)
            .addAxis("Security Level", new String[] {"Protected", "Confidential", "Top Secret"}, true)
            .addAxis("Department", new String[] {"FIN", "MKG", "HR"}, false);
    }

    private String accessPolicyProtected() throws CloudproofException {
        return "(Department::FIN || Department::MKG) && Security Level::Protected";
    }

    private AccessPolicy accessPolicyConfidential() throws CloudproofException {
        return new And(new Attr("Department", "FIN"), new Attr("Security Level", "Confidential"));
    }

    @Test
    public void testPolicy() throws Exception {
        Policy policy = policy();

        ObjectMapper mapper = new ObjectMapper();
        String str = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(policy);
        logger.info(str);
        // make sure the correct serializer is used
        JSONObject json = new JSONObject(str);
        assertTrue(json.has("last_attribute_value"));
        assertTrue(json.has("max_attribute_creations"));
        assertTrue(json.has("axes"));

        VendorAttribute va = policy.toVendorAttribute();
        String vaStr = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(va);
        logger.info(vaStr);
    }

    @Test
    public void testAttributesSerde() throws Exception {
        Attributes attributes = new Attributes(ObjectType.Private_Key, Optional.of(CryptographicAlgorithm.CoverCrypt));
        attributes.keyFormatType(Optional.of(KeyFormatType.CoverCryptSecretKey));
        attributes.vendorAttributes(
            Optional.of(new VendorAttribute[] {Attr.toVendorAttribute(new Attr[] {new Attr("Department", "MKG")},
                VendorAttribute.VENDOR_ATTR_COVER_CRYPT_ATTR)}));
        ObjectMapper mapper = new ObjectMapper();
        String str = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(attributes);
        logger.info(str);
        String hexValue = "5B224465706172746D656E743A3A4D4B47225D";
        String v = new String(Hex.decodeHex(hexValue), StandardCharsets.UTF_8);
        logger.info(v);
        // Attributes rec = mapper.readValue(str, Attributes.class);
        // logger.info(rec.getVendorAttributes().get()[0].toString());
    }

    @Test
    public void testKeysImportExport() throws Exception {

        if (!TestUtils.serverAvailable(TestUtils.kmsServerUrl())) {
            System.out.println("No KMS Server: ignoring");
            return;
        }

        Policy pg = policy();

        KmsClient kmsClient = new KmsClient(TestUtils.kmsServerUrl(), TestUtils.apiKey());

        String[] ids = kmsClient.createCoverCryptMasterKeyPair(pg);
        logger.info("Created Master Key: Private Key ID: " + ids[0] + ", Public Key ID: " + ids[1]);

        String privateMasterKeyUniqueIdentifier = ids[0];
        PrivateKey privateMasterKey = kmsClient.retrieveCoverCryptPrivateMasterKey(privateMasterKeyUniqueIdentifier);
        assertEquals(KeyFormatType.CoverCryptSecretKey, privateMasterKey.getKeyBlock().getKeyFormatType());
        assertEquals(CryptographicAlgorithm.CoverCrypt, privateMasterKey.getKeyBlock().getCryptographicAlgorithm());

        String publicMasterKeyUniqueIdentifier = ids[1];
        PublicKey publicMasterKey = kmsClient.retrieveCoverCryptPublicMasterKey(publicMasterKeyUniqueIdentifier);
        assertEquals(KeyFormatType.CoverCryptPublicKey, publicMasterKey.getKeyBlock().getKeyFormatType());
        assertEquals(CryptographicAlgorithm.CoverCrypt, publicMasterKey.getKeyBlock().getCryptographicAlgorithm());

        // try reimporting the public key with the same ID
        try {
            kmsClient.importCoverCryptPublicMasterKey(publicMasterKeyUniqueIdentifier, publicMasterKey, false);
        } catch (CloudproofException e) {
            // expected cannot re-insert with the same id if replaceExisting is false
        } catch (Exception e) {
            throw e;
        }
        // allow overwrite
        String publicMasterKeyUniqueIdentifier_ = kmsClient.importCoverCryptPublicMasterKey(
            publicMasterKeyUniqueIdentifier,
            publicMasterKey, true);
        logger.info("Imported Public Key with id: " + publicMasterKeyUniqueIdentifier_);
        // retrieve it again
        PublicKey publicMasterKey_ = kmsClient.retrieveCoverCryptPublicMasterKey(publicMasterKeyUniqueIdentifier);
        assertEquals(ObjectType.Public_Key, publicMasterKey_.getObjectType());
        assertEquals(KeyFormatType.CoverCryptPublicKey, publicMasterKey_.getKeyBlock().getKeyFormatType());
        assertEquals(CryptographicAlgorithm.CoverCrypt, publicMasterKey_.getKeyBlock().getCryptographicAlgorithm());

        // User decryption key
        String userDecryptionKeyUniqueIdentifier = kmsClient.createCoverCryptUserDecryptionKey(accessPolicyProtected(),
            privateMasterKeyUniqueIdentifier);
        logger.info("Created User Decryption Key with id: " + userDecryptionKeyUniqueIdentifier);
        // ... retrieve it
        PrivateKey userDecryptionKey = kmsClient.retrieveCoverCryptUserDecryptionKey(userDecryptionKeyUniqueIdentifier);
        assertEquals(KeyFormatType.CoverCryptSecretKey, userDecryptionKey.getKeyBlock().getKeyFormatType());
        assertEquals(CryptographicAlgorithm.CoverCrypt, userDecryptionKey.getKeyBlock().getCryptographicAlgorithm());
        KeyValue keyValue = (KeyValue) userDecryptionKey.getKeyBlock().getKeyValue();
        VendorAttribute[] vendorAttributes = keyValue.getAttributes().get().getVendorAttributes().get();
        // TODO better check on Vendor Attributes
        logger.info(() -> Arrays.asList(vendorAttributes).toString());

    }

    @Test
    public void testKmsEncryptDecrypt() throws Exception {

        if (!TestUtils.serverAvailable(TestUtils.kmsServerUrl())) {
            System.out.println("No KMS Server: ignoring");
            return;
        }

        Policy pg = policy();

        KmsClient kmsClient = new KmsClient(TestUtils.kmsServerUrl(), TestUtils.apiKey());

        String[] ids = kmsClient.createCoverCryptMasterKeyPair(pg);
        logger.info("Created Master Key: Private Key ID: " + ids[0] + ", Public Key ID: " + ids[1]);

        String privateMasterKeyID = ids[0];
        PrivateKey privateMasterKey = kmsClient.retrieveCoverCryptPrivateMasterKey(privateMasterKeyID);
        assertEquals(KeyFormatType.CoverCryptSecretKey, privateMasterKey.getKeyBlock().getKeyFormatType());
        assertEquals(CryptographicAlgorithm.CoverCrypt, privateMasterKey.getKeyBlock().getCryptographicAlgorithm());
        Resources.write_resource("cover_crypt/private_master_key.json",
            privateMasterKey.toJson().getBytes(StandardCharsets.UTF_8));

        String publicMasterKeyUniqueIdentifier = ids[1];
        PublicKey publicMasterKey = kmsClient.retrieveCoverCryptPublicMasterKey(publicMasterKeyUniqueIdentifier);
        assertEquals(KeyFormatType.CoverCryptPublicKey, publicMasterKey.getKeyBlock().getKeyFormatType());
        assertEquals(CryptographicAlgorithm.CoverCrypt, publicMasterKey.getKeyBlock().getCryptographicAlgorithm());
        Resources.write_resource("cover_crypt/public_master_key.json",
            publicMasterKey.toJson().getBytes(StandardCharsets.UTF_8));

        // encryption
        String protected_fin_data = "protected_fin_attributes";
        String protected_fin_enc_policy = "Department::FIN && Security Level::Protected";
        byte[] protected_fin_ct = kmsClient.coverCryptEncrypt(publicMasterKeyUniqueIdentifier,
            protected_fin_data.getBytes(StandardCharsets.UTF_8), protected_fin_enc_policy);

        String confidential_fin_data = "confidential_fin_attributes";
        String confidential_fin_enc_policy = "Department::FIN && Security Level::Confidential";
        byte[] confidential_fin_ct = kmsClient.coverCryptEncrypt(publicMasterKeyUniqueIdentifier,
            confidential_fin_data.getBytes(StandardCharsets.UTF_8), confidential_fin_enc_policy);

        // User decryption key Protected, FIN, MKG
        String fin_mkg_protected_user_key = kmsClient.createCoverCryptUserDecryptionKey(accessPolicyProtected(),
            privateMasterKeyID);
        PrivateKey userKey_1 = kmsClient.retrieveCoverCryptUserDecryptionKey(fin_mkg_protected_user_key);
        Resources.write_resource("cover_crypt/fin_mkg_protected_user_key.json",
            userKey_1.toJson().getBytes(StandardCharsets.UTF_8));

        // User decryption key Confidential, FIN
        String fin_confidential_user_key = kmsClient.createCoverCryptUserDecryptionKey(accessPolicyConfidential(),
            privateMasterKeyID);
        PrivateKey userKey_2 = kmsClient.retrieveCoverCryptUserDecryptionKey(fin_confidential_user_key);
        Resources.write_resource("cover_crypt/fin_confidential_user_key.json",
            userKey_2.toJson().getBytes(StandardCharsets.UTF_8));

        // User decryption key Protected should be able to decrypt protected_fin_ct
        String plaintext_1_1 = new String(kmsClient.coverCryptDecrypt(fin_mkg_protected_user_key, protected_fin_ct)
            .getPlaintext(), StandardCharsets.UTF_8);
        assertEquals(protected_fin_data, plaintext_1_1);
        // User decryption key Confidential should be able to decrypt protected_fin_ct
        String plaintext_1_2 = new String(kmsClient.coverCryptDecrypt(fin_confidential_user_key, protected_fin_ct)
            .getPlaintext(), StandardCharsets.UTF_8);
        assertEquals(protected_fin_data, plaintext_1_2);

        // User decryption key Protected should not be able to decrypt
        // confidential_fin_ct
        try {
            new String(kmsClient.coverCryptDecrypt(fin_mkg_protected_user_key, confidential_fin_ct)
                .getPlaintext(), StandardCharsets.UTF_8);
            throw new RuntimeException("User with key Confidential should not be able to decrypt data Confidential");
        } catch (CloudproofException e) {
            // fine: should not be able to decrypt
        }

        // User decryption key Confidential should not be able to decrypt
        // confidential_fin_ct
        String plaintext_2_2 = new String(kmsClient.coverCryptDecrypt(fin_confidential_user_key, confidential_fin_ct)
            .getPlaintext(), StandardCharsets.UTF_8);
        assertEquals(confidential_fin_data, plaintext_2_2);
    }

    @Test
    public void testKmsEncryptDecryptMetaData() throws Exception {

        if (!TestUtils.serverAvailable(TestUtils.kmsServerUrl())) {
            System.out.println("No KMS Server: ignoring");
            return;
        }

        Policy pg = policy();

        KmsClient kmsClient = new KmsClient(TestUtils.kmsServerUrl(), TestUtils.apiKey());

        String[] ids = kmsClient.createCoverCryptMasterKeyPair(pg);
        logger.info("Created Master Key: Private Key ID: " + ids[0] + ", Public Key ID: " + ids[1]);
        String privateMasterKeyID = ids[0];
        String publicMasterKeyUniqueIdentifier = ids[1];

        // encryption
        byte[] protected_fin_data = "protected_fin_attributes".getBytes(StandardCharsets.UTF_8);
        String protected_fin_enc_policy = "Department::FIN && Security Level::Protected";
        byte[] authenticationData = "authentication".getBytes(StandardCharsets.UTF_8);
        byte[] headerMetaData = "headerMeta".getBytes(StandardCharsets.UTF_8);
        byte[] protected_fin_ct = kmsClient.coverCryptEncrypt(publicMasterKeyUniqueIdentifier,
            protected_fin_data, protected_fin_enc_policy, authenticationData, headerMetaData);

        // User decryption key Protected, FIN, MKG
        String fin_mkg_protected_user_key = kmsClient.createCoverCryptUserDecryptionKey(accessPolicyProtected(),
            privateMasterKeyID);
        PrivateKey userKey_1 = kmsClient.retrieveCoverCryptUserDecryptionKey(fin_mkg_protected_user_key);
        Resources.write_resource("cover_crypt/fin_mkg_protected_user_key.json",
            userKey_1.toJson().getBytes(StandardCharsets.UTF_8));

        // User decryption key Protected should be able to decrypt protected_fin_ct
        DecryptedData decryptedData = kmsClient.coverCryptDecrypt(fin_mkg_protected_user_key, protected_fin_ct,
            authenticationData);
        byte[] plaintext_ = decryptedData.getPlaintext();
        byte[] headerMetadata_ = decryptedData.getHeaderMetaData();
        assertArrayEquals(protected_fin_data, plaintext_);
        assertArrayEquals(headerMetaData, headerMetadata_);
    }

    @Test
    public void testAccessPolicy() throws Exception {

        String expected =
            "{\"And\":[{\"Or\":[{\"Attr\":\"Department::FIN\"},{\"Attr\":\"Department::MKG\"}]},{\"Attr\":\"Levels::Sec_level_1\"}]}";

        AccessPolicy accessPolicy = new And(new Or(new Attr("Department", "FIN"), new Attr("Department", "MKG")),
            new Attr("Levels", "Sec_level_1"));

        ObjectMapper mapper = new ObjectMapper();
        String actual = mapper.writeValueAsString(accessPolicy);
        assertEquals(expected, actual);
    }

}
