/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.api.schema.altair;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES48;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class SyncCommittee {
  @JsonProperty("pubkeys")
  @ArraySchema(
      schema = @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48))
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
    pubkeys =
        committee.getPubkeys().asList().stream()
            .map(k -> new BLSPubKey(k.getBLSPublicKey()))
            .collect(Collectors.toList());
    aggregatePubkey = new BLSPubKey(committee.getAggregatePubkey().getBLSPublicKey());
  }

  public tech.pegasys.teku.spec.datastructures.state.SyncCommittee asInternalSyncCommittee(
      final tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema schema) {
    SszPublicKey aggregate = new SszPublicKey(aggregatePubkey.asBLSPublicKey());
    List<SszPublicKey> committee =
        pubkeys.stream()
            .map(key -> new SszPublicKey(key.asBLSPublicKey()))
            .collect(Collectors.toList());
    return schema.create(committee, aggregate);
  }
}
