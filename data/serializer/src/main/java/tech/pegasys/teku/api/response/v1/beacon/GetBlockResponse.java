package tech.pegasys.teku.api.response.v1.beacon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;

public class GetBlockResponse {
  @JsonProperty("data")
  public final SignedBeaconBlock data;

  @JsonCreator
  public GetBlockResponse(@JsonProperty("data") final SignedBeaconBlock data) {
    this.data = data;
  }
}
