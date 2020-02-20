package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Crypto {
    private Kdf kdf;
    private Checksum checksum;
    private Cipher cipher;

    @JsonCreator
    public Crypto(@JsonProperty(value="kdf", required = true) final Kdf kdf,
                  @JsonProperty(value="checksum", required = true) final Checksum checksum,
                  @JsonProperty(value="cipher", required = true) final Cipher cipher) {
        this.kdf = kdf;
        this.checksum = checksum;
        this.cipher = cipher;
    }

    public Kdf getKdf() {
        return kdf;
    }

    public Checksum getChecksum() {
        return checksum;
    }

    public Cipher getCipher() {
        return cipher;
    }
}
