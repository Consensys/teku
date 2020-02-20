package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

// Required because base class' usage of custom deserializer which produces an infinite loop
@JsonDeserialize(using = JsonDeserializer.None.class)
public class SCryptParams extends KdfParams {
    private Integer n;
    private Integer p;
    private Integer r;

    @JsonCreator
    public SCryptParams(@JsonProperty(value="dklen", required = true) final Integer dklen,
                        @JsonProperty(value="n", required = true) final Integer n,
                        @JsonProperty(value="p", required = true) final Integer p,
                        @JsonProperty(value="r", required = true)final Integer r,
                        @JsonProperty(value="salt", required = true) final String salt) {
        super(dklen, salt);
        this.n = n;
        this.p = p;
        this.r = r;
    }

    public Integer getN() {
        return n;
    }

    public Integer getP() {
        return p;
    }

    public Integer getR() {
        return r;
    }

}
