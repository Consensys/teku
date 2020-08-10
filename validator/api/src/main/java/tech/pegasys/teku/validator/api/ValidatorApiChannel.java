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

package tech.pegasys.teku.validator.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.channels.ChannelInterface;

public interface ValidatorApiChannel extends ChannelInterface {
  SafeFuture<Optional<ForkInfo>> getForkInfo();

  SafeFuture<Optional<List<ValidatorDuties>>> getDuties(
      UInt64 epoch, Collection<BLSPublicKey> publicKeys);

  SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      UInt64 slot, BLSSignature randaoReveal, Optional<Bytes32> graffiti);

  SafeFuture<Optional<Attestation>> createUnsignedAttestation(UInt64 slot, int committeeIndex);

  SafeFuture<Optional<Attestation>> createAggregate(Bytes32 attestationHashTreeRoot);

  void subscribeToBeaconCommitteeForAggregation(int committeeIndex, UInt64 aggregationSlot);

  void subscribeToPersistentSubnets(Set<SubnetSubscription> subnetSubscriptions);

  void sendSignedAttestation(Attestation attestation);

  void sendSignedAttestation(Attestation attestation, Optional<Integer> validatorIndex);

  void sendAggregateAndProof(SignedAggregateAndProof aggregateAndProof);

  void sendSignedBlock(SignedBeaconBlock block);
}
