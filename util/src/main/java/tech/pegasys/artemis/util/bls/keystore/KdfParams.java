package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

// @JsonSerialize(using = KdfParamsJsonSerializer.class)
@JsonDeserialize(using = KdfParamsJsonDeserializer.class)
public abstract class KdfParams extends Params {
    private Integer dklen;
    private String salt;

    public KdfParams(final Integer dklen, final String salt) {
        this.dklen = dklen;
        this.salt = salt;
    }

    public Integer getDklen() {
        return dklen;
    }

    public String getSalt() {
        return salt;
    }
}
