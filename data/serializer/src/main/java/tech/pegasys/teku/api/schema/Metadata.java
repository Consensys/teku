/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_UINT64;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.altair.MetadataMessageAltair;

@Schema(
    description =
        "Based on eth2 [Metadata object]"
            + "(https://github.com/ethereum/consensus-specs/blob/v0.12.2/specs/phase0/p2p-interface.md#metadata)")
public class Metadata {
  @JsonProperty("seq_number")
  @Schema(
      type = "string",
      pattern = PATTERN_UINT64,
      description =
          "Uint64 starting at 0 used to version the node's metadata. "
              + "If any other field in the local MetaData changes, the node MUST increment seq_number by 1.")
  public final String sequenceNumber;

  @JsonProperty("attnets")
  @Schema(
      type = "string",
      pattern = "^0x[a-fA-F0-9]{2,}$",
      description =
          "Bitvector representing the node's persistent attestation subnet subscriptions.")
  public final String attestationSubnetSubscriptions;

  @JsonProperty("syncnets")
  @Schema(
      type = "string",
      pattern = "^0x[a-fA-F0-9]{2,}$",
      description =
          "Bitvector representing the node's persistent attestation subnet subscriptions.")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public final String syncCommitteeSubscriptions;

  @JsonCreator
  public Metadata(
      @JsonProperty("seq_number") final String sequenceNumber,
      @JsonProperty("attnets") final String attestationSubnetSubscriptions,
      @JsonProperty("syncnets") final String syncCommitteeSubscriptions) {
    this.sequenceNumber = sequenceNumber;
    this.attestationSubnetSubscriptions = attestationSubnetSubscriptions;
    this.syncCommitteeSubscriptions = syncCommitteeSubscriptions;
  }

  public Metadata(final MetadataMessage metadataMessage) {
    this.sequenceNumber = metadataMessage.getSeqNumber().toString();
    this.attestationSubnetSubscriptions =
        metadataMessage.getAttnets().sszSerialize().toHexString().toLowerCase();
    if (metadataMessage instanceof MetadataMessageAltair) {
      this.syncCommitteeSubscriptions =
          ((MetadataMessageAltair) metadataMessage)
              .getSyncnets()
              .sszSerialize()
              .toHexString()
              .toLowerCase();
    } else {
      this.syncCommitteeSubscriptions = null;
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Metadata metadata = (Metadata) o;
    return Objects.equals(sequenceNumber, metadata.sequenceNumber)
        && Objects.equals(attestationSubnetSubscriptions, metadata.attestationSubnetSubscriptions)
        && Objects.equals(syncCommitteeSubscriptions, metadata.syncCommitteeSubscriptions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sequenceNumber, attestationSubnetSubscriptions, syncCommitteeSubscriptions);
  }
}
