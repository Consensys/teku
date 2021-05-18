package tech.pegasys.teku.api.schema.altair;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.BLSPubKey;

import java.util.List;
import java.util.stream.Collectors;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES48;

public class SyncCommittee {
  @JsonProperty("pubkeys")
  @ArraySchema(schema=@Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48))
  public final List<BLSPubKey> pubkeys;

  @JsonProperty("aggregate_pubkey")
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48)
  public final BLSPubKey aggregatePubkey;

  @JsonCreator
  public SyncCommittee(
      @JsonProperty("pubkeys") final List<BLSPubKey> pubkeys,
      @JsonProperty("aggregate_pubkey") final BLSPubKey aggregatePubkey) {
    this.pubkeys = pubkeys;
    this.aggregatePubkey = aggregatePubkey;
  }

  public SyncCommittee(final tech.pegasys.teku.spec.datastructures.state.SyncCommittee committee) {
    pubkeys = committee.getPubkeys().asList().stream()
        .map(k -> new BLSPubKey(k.getBLSPublicKey()))
        .collect(Collectors.toList());
    //FIXME 3994 getPubkeyAggregate should have 1 key
    aggregatePubkey = new BLSPubKey(committee.getPubkeyAggregates().get(0).getBLSPublicKey());
  }
}
