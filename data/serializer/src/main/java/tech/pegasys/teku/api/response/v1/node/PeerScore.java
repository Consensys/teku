package tech.pegasys.teku.api.response.v1.node;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PeerScore {

    @JsonProperty("peer_id")
    @Schema(
            type = "string",
            description =
                    "Cryptographic hash of a peer’s public key. "
                            + "'[Read more](https://docs.libp2p.io/concepts/peer-id/)",
            example = "QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N")
    public final String peerId;

    @JsonProperty("gossip_score")
    @Schema(
            type = "string",
            description = "Gossip score for the associated peer.",
            example = "1.0")
    public final String gossipScore;

    @JsonCreator
    public PeerScore(
            @JsonProperty("peer_id") final String peerId,
            @JsonProperty("gossip_score") final String gossipScore) {
        this.peerId = peerId;
        this.gossipScore = gossipScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerScore)) return false;
        PeerScore peerScore = (PeerScore) o;
        return Objects.equals(peerId, peerScore.peerId) && Objects.equals(gossipScore, peerScore.gossipScore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId, gossipScore);
    }
}