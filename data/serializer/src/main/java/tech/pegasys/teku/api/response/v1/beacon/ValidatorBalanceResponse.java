package tech.pegasys.teku.api.response.v1.beacon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;

public class ValidatorBalanceResponse {
  @JsonProperty("index")
  @Schema(
          type = "string",
          example = EXAMPLE_UINT64,
          description = "Index of validator in validator registry.")
  public final UInt64 index;

  @JsonProperty("balance")
  @Schema(
          type = "string",
          example = EXAMPLE_UINT64,
          description = "Current validator balance in gwei.")
  public final UInt64 balance;

  @JsonCreator
  public ValidatorBalanceResponse(
          @JsonProperty("index") final UInt64 index,
          @JsonProperty("balance") final UInt64 balance) {
    this.index = index;
    this.balance = balance;
  }

  public static ValidatorBalanceResponse fromState(final BeaconState state, final Integer index) {
    return new ValidatorBalanceResponse(UInt64.valueOf(index), state.getBalances().get(index));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ValidatorBalanceResponse)) return false;
    ValidatorBalanceResponse that = (ValidatorBalanceResponse) o;
    return Objects.equal(index, that.index) &&
            Objects.equal(balance, that.balance);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(index, balance);
  }
}

