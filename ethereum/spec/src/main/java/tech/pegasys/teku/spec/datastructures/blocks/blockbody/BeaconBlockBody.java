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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodyMerge;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.ssz.SszContainer;
import tech.pegasys.teku.ssz.SszList;

public interface BeaconBlockBody extends SszContainer {
  BLSSignature getRandaoReveal();

  Eth1Data getEth1Data();

  Bytes32 getGraffiti();

  SszList<ProposerSlashing> getProposerSlashings();

  SszList<AttesterSlashing> getAttesterSlashings();

  SszList<Attestation> getAttestations();

  SszList<Deposit> getDeposits();

  SszList<SignedVoluntaryExit> getVoluntaryExits();

  default Optional<ExecutionPayload> getOptionalExecutionPayload() {
    return Optional.empty();
  }

  @Override
  BeaconBlockBodySchema<? extends BeaconBlockBody> getSchema();

  default Optional<BeaconBlockBodyAltair> toVersionAltair() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodyMerge> toVersionMerge() {
    return Optional.empty();
  }
}
