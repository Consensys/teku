package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CryptoFunction {
    @JsonProperty("sha256")
    SHA256,
    @JsonProperty("pbkdf2")
    PBKDF2,
    @JsonProperty("scrypt")
    SCRYPT,
    @JsonProperty("aes-128-ctr")
    AES_128_CTR
}
