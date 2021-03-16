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

package tech.pegasys.teku.spec.datastructures.state.beaconstate;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.ssz.impl.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.impl.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.ssz.schema.collections.SszPrimitiveVectorSchema;
import tech.pegasys.teku.ssz.schema.collections.SszUInt64ListSchema;

public interface BeaconStateSchema<T extends BeaconState, TMutable extends MutableBeaconState>
    extends SszContainerSchema<T> {
  TMutable createBuilder();

  T createEmpty();

  default SszBytes32VectorSchema<?> getBlockRootsSchema() {
    return (SszBytes32VectorSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.BLOCK_ROOTS.name()));
  }

  default SszBytes32VectorSchema<?> getStateRootsSchema() {
    return (SszBytes32VectorSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.STATE_ROOTS.name()));
  }

  @SuppressWarnings("unchecked")
  default SszPrimitiveListSchema<Bytes32, SszBytes32, ?> getHistoricalRootsSchema() {
    return (SszPrimitiveListSchema<Bytes32, SszBytes32, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS.name()));
  }

  @SuppressWarnings("unchecked")
  default SszListSchema<Eth1Data, ?> getEth1DataVotesSchema() {
    return (SszListSchema<Eth1Data, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES.name()));
  }

  @SuppressWarnings("unchecked")
  default SszListSchema<Validator, ?> getValidatorsSchema() {
    return (SszListSchema<Validator, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.VALIDATORS.name()));
  }

  default SszUInt64ListSchema<?> getBalancesSchema() {
    return (SszUInt64ListSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.BALANCES.name()));
  }

  default SszBytes32VectorSchema<?> getRandaoMixesSchema() {
    return (SszBytes32VectorSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.RANDAO_MIXES.name()));
  }

  @SuppressWarnings("unchecked")
  default SszPrimitiveVectorSchema<UInt64, SszUInt64, ?> getSlashingsSchema() {
    return (SszPrimitiveVectorSchema<UInt64, SszUInt64, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.SLASHINGS.name()));
  }

  @SuppressWarnings("unchecked")
  default SszListSchema<PendingAttestation, ?> getPreviousEpochAttestationsSchema() {
    return (SszListSchema<PendingAttestation, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name()));
  }

  @SuppressWarnings("unchecked")
  default SszListSchema<PendingAttestation, ?> getCurrentEpochAttestationsSchema() {
    return (SszListSchema<PendingAttestation, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name()));
  }

  default SszBitvectorSchema<?> getJustificationBitsSchema() {
    return (SszBitvectorSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.JUSTIFICATION_BITS.name()));
  }
}
