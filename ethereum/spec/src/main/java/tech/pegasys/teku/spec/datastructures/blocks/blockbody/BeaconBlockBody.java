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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BlindedBeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BeaconBlockBodyEip4844;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BlindedBeaconBlockBodyEip4844;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public interface BeaconBlockBody extends SszContainer {
  BLSSignature getRandaoReveal();

  SszSignature getRandaoRevealSsz();

  Eth1Data getEth1Data();

  Bytes32 getGraffiti();

  SszBytes32 getGraffitiSsz();

  SszList<ProposerSlashing> getProposerSlashings();

  SszList<AttesterSlashing> getAttesterSlashings();

  SszList<Attestation> getAttestations();

  SszList<Deposit> getDeposits();

  SszList<SignedVoluntaryExit> getVoluntaryExits();

  default Optional<SyncAggregate> getOptionalSyncAggregate() {
    return Optional.empty();
  }

  default Optional<ExecutionPayload> getOptionalExecutionPayload() {
    return Optional.empty();
  }

  default Optional<ExecutionPayloadHeader> getOptionalExecutionPayloadHeader() {
    return Optional.empty();
  }

  default Optional<ExecutionPayloadSummary> getOptionalExecutionPayloadSummary() {
    return Optional.empty();
  }

  Optional<SszList<SignedBlsToExecutionChange>> getOptionalBlsToExecutionChanges();

  default boolean isBlinded() {
    return false;
  }

  @Override
  BeaconBlockBodySchema<? extends BeaconBlockBody> getSchema();

  default Optional<BeaconBlockBodyAltair> toVersionAltair() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodyBellatrix> toVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodyBellatrix> toBlindedVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodyCapella> toVersionCapella() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodyCapella> toBlindedVersionCapella() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodyEip4844> toVersionEip4844() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodyEip4844> toBlindedVersionEip4844() {
    return Optional.empty();
  }
}
