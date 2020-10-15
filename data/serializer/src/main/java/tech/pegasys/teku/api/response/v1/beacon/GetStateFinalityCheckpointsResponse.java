package tech.pegasys.teku.api.response.v1.beacon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GetStateFinalityCheckpointsResponse {

  @JsonProperty("data")
  public final FinalityCheckpointsResponse data;

  @JsonCreator
  public GetStateFinalityCheckpointsResponse(@JsonProperty("data") final FinalityCheckpointsResponse data) {
    this.data = data;
  }
}
