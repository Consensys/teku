package tech.pegasys.artemis.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;

import java.util.List;

public class ValidatorsRequest {
  public final UnsignedLong epoch;
  public final List<BLSPubKey> pubkeys;

  @JsonCreator
  public ValidatorsRequest(
      @JsonProperty(value = "epoch", required = true) UnsignedLong epoch,
      @JsonProperty(value = "pubkeys", required = true) final List<BLSPubKey> pubkeys) {
    this.epoch = epoch;
    this.pubkeys = pubkeys;
  }

}
