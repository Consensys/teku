package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Cipher {
    private CryptoFunction cryptoFunction;
    private CipherParam cipherParam;
    private String message;

    @JsonCreator
    public Cipher(@JsonProperty(value="function", required = true) final CryptoFunction cryptoFunction,
                  @JsonProperty(value="params", required = true) final CipherParam cipherParam,
                  @JsonProperty(value="message", required = true) final String message) {
        this.cryptoFunction = cryptoFunction;
        this.cipherParam = cipherParam;
        this.message = message;
    }

    public CryptoFunction getCryptoFunction() {
        return cryptoFunction;
    }

    public CipherParam getCipherParam() {
        return cipherParam;
    }

    public String getMessage() {
        return message;
    }
}
