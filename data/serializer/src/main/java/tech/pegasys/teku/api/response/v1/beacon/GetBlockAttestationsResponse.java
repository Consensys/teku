package tech.pegasys.teku.api.response.v1.beacon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.Attestation;

import java.util.List;

public class GetBlockAttestationsResponse {
  @JsonProperty("data")
  public final List<Attestation> data;

  @JsonCreator
  public GetBlockAttestationsResponse(@JsonProperty("data") final List<Attestation> data) {
    this.data = data;
  }
}
