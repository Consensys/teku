package tech.pegasys.teku.api.response.v1.validator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GetAttesterDutiesResponse {
  public final List<AttesterDuty> data;

  @JsonCreator
  public GetAttesterDutiesResponse(@JsonProperty("data") final List<AttesterDuty> data) {
    this.data = data;
  }
}
