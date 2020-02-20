package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * BLS Key Store implementation as per EIP-2335
 * @see <a href="https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2335.md"> EIP-2335</a>
 */
public class KeyStore {
    private Crypto crypto;
    private String pubkey;
    private String path;
    private UUID uuid;
    private Integer version;

    public KeyStore(@JsonProperty(value="crypto", required = true) final Crypto crypto,
                    @JsonProperty(value="pubkey", required = true) final String pubkey,
                    @JsonProperty(value="path", required = true) final String path,
                    @JsonProperty(value="uuid", required = true) final UUID uuid,
                    @JsonProperty(value="version", required = true, defaultValue = "4") final Integer version) {
        this.crypto = crypto;
        this.pubkey = pubkey;
        this.path = path;
        this.uuid = uuid;
        this.version = version;
    }

    public Crypto getCrypto() {
        return crypto;
    }

    public String getPubkey() {
        return pubkey;
    }

    public String getPath() {
        return path;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Integer getVersion() {
        return version;
    }
}
