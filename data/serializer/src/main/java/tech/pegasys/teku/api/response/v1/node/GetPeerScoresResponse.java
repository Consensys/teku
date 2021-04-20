package tech.pegasys.teku.api.response.v1.node;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GetPeerScoresResponse {
    public final List<PeerScore> data;

    @JsonCreator
    public GetPeerScoresResponse(@JsonProperty("data") final List<PeerScore> data) {
        this.data = data;
    }
}
