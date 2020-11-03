package tech.pegasys.teku.api.response.v1.beacon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.Root;

public class GetBlockRootResponse {
  @JsonProperty("data")
  public final Root data;

  @JsonCreator
  public GetBlockRootResponse(@JsonProperty("data") final Root data) {
    this.data = data;
  }
}
