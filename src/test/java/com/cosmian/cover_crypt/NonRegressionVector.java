package com.cosmian.cover_crypt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.cosmian.CloudproofException;
import com.cosmian.Resources;
import com.cosmian.jna.abe.CoverCrypt;
import com.cosmian.jna.abe.MasterKeys;
import com.cosmian.rest.abe.policy.Policy;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NonRegressionVector {
    static final CoverCrypt coverCrypt = new CoverCrypt();

    @JsonProperty("public_key")
    private byte[] publicKey;

    @JsonProperty("master_secret_key")
    private byte[] masterSecretKey;

    @JsonProperty("policy")
    private byte[] policy;

    @JsonProperty("top_secret_mkg_fin_key")
    private UserSecretKeyTestVector topSecretMkgFinKey;

    @JsonProperty("medium_secret_mkg_key")
    private UserSecretKeyTestVector mediumSecretMkgKey;

    @JsonProperty("top_secret_fin_key")
    private UserSecretKeyTestVector topSecretFinKey;

    @JsonProperty("low_secret_mkg_test_vector")
    private EncryptionTestVector lowSecretMkgTestVector;

    @JsonProperty("top_secret_mkg_test_vector")
    private EncryptionTestVector topSecretMkgTestVector;

    @JsonProperty("low_secret_fin_test_vector")
    private EncryptionTestVector lowSecretFinTestVector;

    public byte[] getPublicKey() {
        return this.publicKey;
    }

    public byte[] getMasterSecretKey() {
        return masterSecretKey;
    }

    public byte[] getPolicy() {
        return policy;
    }

    public UserSecretKeyTestVector getTopSecretMkgFinKey() {
        return topSecretMkgFinKey;
    }

    public UserSecretKeyTestVector getMediumSecretMkgKey() {
        return mediumSecretMkgKey;
    }

    public UserSecretKeyTestVector getTopSecretFinKey() {
        return topSecretFinKey;
    }

    public EncryptionTestVector getLowSecretMkgTestVector() {
        return lowSecretMkgTestVector;
    }

    public EncryptionTestVector getTopSecretMkgTestVector() {
        return topSecretMkgTestVector;
    }

    public EncryptionTestVector getLowSecretFinTestVector() {
        return lowSecretFinTestVector;
    }

    public static NonRegressionVector generate() throws JsonProcessingException, CloudproofException {
        Policy policy = new Policy(100)
            .addAxis("Security Level",
                new String[] {
                    "Protected",
                    "Low Secret",
                    "Medium Secret",
                    "High Secret",
                    "Top Secret",
                },
                true)
            .addAxis("Department", new String[] {
                "R&D",
                "HR",
                "MKG",
                "FIN"
            }, false);

        // Generate the master keys
        MasterKeys masterKeys = coverCrypt.generateMasterKeys(policy);

        // Generate user decryption keys
        NonRegressionVector nrv = new NonRegressionVector();
        nrv.masterSecretKey = masterKeys.getPrivateKey();
        nrv.publicKey = masterKeys.getPublicKey();
        nrv.policy = new ObjectMapper().writeValueAsString(policy).getBytes(StandardCharsets.UTF_8);

        nrv.topSecretMkgFinKey =
            UserSecretKeyTestVector.generate(masterKeys.getPrivateKey(), policy,
                "(Department::MKG || Department:: FIN) && Security Level::Top Secret");
        nrv.mediumSecretMkgKey = UserSecretKeyTestVector
            .generate(masterKeys.getPrivateKey(), policy,
                "Security Level::Medium Secret && Department::MKG");
        nrv.topSecretFinKey = UserSecretKeyTestVector
            .generate(masterKeys.getPrivateKey(), policy,
                "Security Level::Top Secret && Department::FIN");

        // Generate ciphertexts
        nrv.topSecretMkgTestVector = EncryptionTestVector.generate(policy, masterKeys.getPublicKey(),
            "Department::MKG && Security Level::Top Secret", "TopSecretMkgPlaintext", new byte[] {1, 2, 3, 4, 5,
                6},
            new byte[] {7, 8, 9, 10, 11});
        nrv.lowSecretMkgTestVector = EncryptionTestVector.generate(policy, masterKeys.getPublicKey(),
            "Department::MKG && Security Level::Low Secret", "LowSecretMkgPlaintext",
            new byte[] {1, 2, 3, 4, 5, 6},
            new byte[] {});
        nrv.lowSecretFinTestVector = EncryptionTestVector.generate(policy, masterKeys.getPublicKey(),
            "Department::FIN && Security Level::Low Secret", "LowSecretFinPlaintext",
            new byte[] {},
            new byte[] {});

        return nrv;
    }

    public static void verify(String filename) throws IOException, CloudproofException {
        String json = Resources.load_file(filename);
        NonRegressionVector nrv = NonRegressionVector.fromJson(json);
        // top_secret_fin_key
        nrv.getLowSecretFinTestVector().decrypt(nrv.getTopSecretFinKey().getKey());
        try {
            nrv.getLowSecretMkgTestVector().decrypt(nrv.getTopSecretFinKey().getKey());
            throw new CloudproofException("Should not be able to decrypt");
        } catch (CloudproofException e) {
            // ... failing expected
        }
        try {
            nrv.getTopSecretMkgTestVector().decrypt(nrv.getTopSecretFinKey().getKey());
            throw new CloudproofException("Should not be able to decrypt");
        } catch (CloudproofException e) {
            // ... failing expected
        }

        // top_secret_mkg_fin_key
        nrv.getLowSecretFinTestVector().decrypt(nrv.getTopSecretMkgFinKey().getKey());
        nrv.getLowSecretMkgTestVector().decrypt(nrv.getTopSecretMkgFinKey().getKey());
        nrv.getTopSecretMkgTestVector().decrypt(nrv.getTopSecretMkgFinKey().getKey());

        // medium_secret_mkg_key
        try {
            nrv.getLowSecretFinTestVector().decrypt(nrv.getMediumSecretMkgKey().getKey());
            throw new CloudproofException("Should not be able to decrypt");
        } catch (CloudproofException e) {
            // ... failing expected
        }
        nrv.getLowSecretMkgTestVector().decrypt(nrv.getMediumSecretMkgKey().getKey());
        try {
            nrv.getTopSecretMkgTestVector().decrypt(nrv.getMediumSecretMkgKey().getKey());
            throw new CloudproofException("Should not be able to decrypt");
        } catch (CloudproofException e) {
            // ... failing expected
        }
        System.out.println("... OK: " + filename);
    }

    /**
     * This method is mostly used for local tests and serialization.
     *
     * @return the JSON string
     * @throws CloudproofException if the serialization fails
     */
    public String toJson() throws CloudproofException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new CloudproofException("Failed serializing to JSON the MasterKeys.class: " + e.getMessage(), e);
        }
    }

    public static NonRegressionVector fromJson(String json) throws CloudproofException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(json, NonRegressionVector.class);
        } catch (JsonProcessingException e) {
            throw new CloudproofException(
                "Failed deserializing from JSON the NonRegressionVector.class " + ": " + e.getMessage(),
                e);
        }
    }

}
