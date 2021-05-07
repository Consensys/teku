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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES_SSZ;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.schema.BLSSignature;

public class SyncAggregate {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES_SSZ)
  @JsonProperty("sync_committee_bits")
  public Bytes syncCommitteeBits;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  @JsonProperty("sync_committee_signature")
  public final BLSSignature syncCommitteeSignature;

  @JsonCreator
  public SyncAggregate(
      @JsonProperty("sync_committee_bits") final Bytes syncCommitteeBits,
      @JsonProperty("sync_committee_signature") final BLSSignature syncCommitteeSignature) {
    this.syncCommitteeBits = syncCommitteeBits;
    this.syncCommitteeSignature = syncCommitteeSignature;
  }

  public SyncAggregate(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate
          aggregate) {
    this.syncCommitteeSignature =
        new BLSSignature(aggregate.getSyncCommitteeSignature().getSignature());
    this.syncCommitteeBits = aggregate.getSyncCommitteeBits().sszSerialize();
  }
}
