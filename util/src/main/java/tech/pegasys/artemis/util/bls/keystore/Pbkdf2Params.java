package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

// Required because base class' usage of custom deserializer which produces an infinite loop
@JsonDeserialize(using = JsonDeserializer.None.class)
public class Pbkdf2Params extends KdfParams {
    private Integer c;
    private String prf;

    @JsonCreator
    public Pbkdf2Params(@JsonProperty(value="dklen", required = true) final Integer dklen,
                        @JsonProperty(value="c", required = true) final Integer c,
                        @JsonProperty(value="prf", required = true) final String prf,
                        @JsonProperty(value="salt", required = true) final String salt) {
        super(dklen, salt);
        this.c = c;
        this.prf = prf;
    }

    public Integer getC() {
        return c;
    }

    public String getPrf() {
        return prf;
    }
}
