package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Checksum {
    private CryptoFunction cryptoFunction;
    private Params params;
    private String message;

    @JsonCreator
    public Checksum(@JsonProperty(value="function", required = true) final CryptoFunction cryptoFunction,
                    @JsonProperty(value="params", required = true) final Params params,
                    @JsonProperty(value="message", required = true) final String message) {
        this.cryptoFunction = cryptoFunction;
        this.params = params;
        this.message = message;
    }

    public CryptoFunction getCryptoFunction() {
        return cryptoFunction;
    }

    public Params getParams() {
        return params;
    }

    public String getMessage() {
        return message;
    }
}
