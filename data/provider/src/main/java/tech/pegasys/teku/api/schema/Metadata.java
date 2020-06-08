/*
 * Copyright 2020 ConsenSys AG.
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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES_SSZ;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;

public class Metadata {
  @JsonProperty("seq_number")
  @Schema(type = "string", format = "uint64")
  public final String sequenceNumber;

  @JsonProperty("attnets")
  @Schema(
      type = "string",
      pattern = "^0x[a-fA-F0-9]{2,}$",
      format = "byte",
      description = DESCRIPTION_BYTES_SSZ)
  public final String attestationSubnetSubscriptions;

  @JsonCreator
  public Metadata(
      @JsonProperty("seq_number") final String sequenceNumber,
      @JsonProperty("attnets") final String attestationSubnetSubscriptions) {
    this.sequenceNumber = sequenceNumber;
    this.attestationSubnetSubscriptions = attestationSubnetSubscriptions;
  }

  public Metadata(final MetadataMessage metadataMessage) {
    this.sequenceNumber = metadataMessage.getSeqNumber().toString();
    this.attestationSubnetSubscriptions =
        metadataMessage.getAttnets().serialize().toHexString().toLowerCase();
  }
}
