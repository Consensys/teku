package tech.pegasys.teku.api.response.v1.beacon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.Checkpoint;

public class FinalityCheckpointsResponse {
  @JsonProperty("previous_justified")
  public final Checkpoint previous_justified;

  @JsonProperty("current_justified")
  public final Checkpoint current_justified;

  @JsonProperty("finalized")
  public final Checkpoint finalized;

  @JsonCreator
  public FinalityCheckpointsResponse(
          @JsonProperty("previous_justified") Checkpoint previous_justified,
          @JsonProperty("current_justified") Checkpoint current_justified,
          @JsonProperty("finalized") Checkpoint finalized) {
    this.previous_justified = previous_justified;
    this.current_justified = current_justified;
    this.finalized = finalized;
  }
}
