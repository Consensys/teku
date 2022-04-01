/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class AttestationEvent extends Event<Attestation> {

  private static final SerializableTypeDefinition<Checkpoint> CHECKPOINT_TYPE =
      SerializableTypeDefinition.object(Checkpoint.class)
          .withField("epoch", UINT64_TYPE, Checkpoint::getEpoch)
          .withField("root", BYTES32_TYPE, Checkpoint::getRoot)
          .build();

  private static final SerializableTypeDefinition<AttestationData> ATTESTATION_DATA_TYPE =
      SerializableTypeDefinition.object(AttestationData.class)
          .withField("slot", UINT64_TYPE, AttestationData::getSlot)
          .withField("index", UINT64_TYPE, AttestationData::getIndex)
          .withField("beacon_block_root", BYTES32_TYPE, AttestationData::getBeacon_block_root)
          .withField("source", CHECKPOINT_TYPE, AttestationData::getSource)
          .withField("target", CHECKPOINT_TYPE, AttestationData::getTarget)
          .build();

  private static final SerializableTypeDefinition<Attestation> EVENT_TYPE =
      SerializableTypeDefinition.object(Attestation.class)
          .withField(
              "aggregation_bits",
              BYTES_TYPE,
              attestation -> attestation.getAggregationBits().sszSerialize())
          .withField(
              "signature",
              BYTES_TYPE,
              attestation -> attestation.getAggregateSignature().toBytesCompressed())
          .withField("data", ATTESTATION_DATA_TYPE, Attestation::getData)
          .build();

  AttestationEvent(Attestation attestation) {
    super(EVENT_TYPE, attestation);
  }
}
