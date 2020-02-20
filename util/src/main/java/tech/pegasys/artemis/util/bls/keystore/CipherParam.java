package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CipherParam {
    private String initializationVector;

    @JsonCreator
    public CipherParam(@JsonProperty(value = "iv", required = true) final String initializationVector) {
        this.initializationVector = initializationVector;
    }

    public String getInitializationVector() {
        return initializationVector;
    }

}
