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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSSignatureDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSSignatureSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;

public class SyncAggregateV1 {
  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = BytesDeserializer.class)
  public Bytes syncCommitteeBits;

  @JsonSerialize(using = BLSSignatureSerializer.class)
  @JsonDeserialize(using = BLSSignatureDeserializer.class)
  public final BLSSignature syncCommitteeSignature;

  @JsonCreator
  public SyncAggregateV1(
      @JsonProperty("syncCommitteeBits") final Bytes syncCommitteeBits,
      @JsonProperty("syncCommitteeSignature") final BLSSignature syncCommitteeSignature) {
    this.syncCommitteeBits = syncCommitteeBits;
    this.syncCommitteeSignature = syncCommitteeSignature;
  }

  public SyncAggregateV1(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate
          aggregate) {
    this.syncCommitteeSignature =
        new BLSSignature(aggregate.getSyncCommitteeSignature().getSignature());
    this.syncCommitteeBits = aggregate.getSyncCommitteeBits().sszSerialize();
  }
}
