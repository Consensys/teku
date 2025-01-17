/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.gossip.forks;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;

public interface GossipForkSubscriptions {

  UInt64 getActivationEpoch();

  void startGossip(Bytes32 genesisValidatorsRoot, boolean isOptimisticHead);

  void stopGossip();

  void stopGossipForOptimisticSync();

  void publishAttestation(ValidatableAttestation attestation);

  SafeFuture<Void> publishBlock(SignedBeaconBlock block);

  default SafeFuture<Void> publishBlobSidecar(final BlobSidecar blobSidecar) {
    return SafeFuture.COMPLETE;
  }

  void subscribeToAttestationSubnetId(int subnetId);

  void unsubscribeFromAttestationSubnetId(int subnetId);

  default void addBlobSidecarGossipManager(final ForkInfo forkInfo) {}

  default void publishSyncCommitteeMessage(final ValidatableSyncCommitteeMessage message) {
    // since Altair
  }

  default void publishSyncCommitteeContribution(final SignedContributionAndProof message) {
    // since Altair
  }

  void publishProposerSlashing(ProposerSlashing message);

  void publishAttesterSlashing(AttesterSlashing message);

  void publishVoluntaryExit(SignedVoluntaryExit message);

  default void subscribeToSyncCommitteeSubnet(final int subnetId) {
    // since Altair
  }

  default void unsubscribeFromSyncCommitteeSubnet(final int subnetId) {
    // since Altair
  }

  default void publishSignedBlsToExecutionChangeMessage(final SignedBlsToExecutionChange message) {}

  default void publishDataColumnSidecar(final DataColumnSidecar blobSidecar) {
    // since Fulu
  }

  default void subscribeToDataColumnSidecarSubnet(final int subnetId) {
    // since Fulu
  }

  default void unsubscribeFromDataColumnSidecarSubnet(final int subnetId) {
    // since Fulu
  }
}
