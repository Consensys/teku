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

import com.google.common.primitives.UnsignedLong;
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
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.util.async.SafeFuture;

public interface ValidatorApiChannel {
  SafeFuture<Optional<ForkInfo>> getForkInfo();

  SafeFuture<Optional<List<ValidatorDuties>>> getDuties(
      UnsignedLong epoch, Collection<BLSPublicKey> publicKeys);

  SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      UnsignedLong slot, BLSSignature randaoReveal, Optional<Bytes32> graffiti);

  SafeFuture<Optional<Attestation>> createUnsignedAttestation(
      UnsignedLong slot, int committeeIndex);

  SafeFuture<Optional<Attestation>> createAggregate(AttestationData attestationData);

  void subscribeToBeaconCommitteeForAggregation(int committeeIndex, UnsignedLong aggregationSlot);

  void subscribeToPersistentSubnets(Set<SubnetSubscription> subnetSubscriptions);

  void sendSignedAttestation(Attestation attestation);

  void sendAggregateAndProof(SignedAggregateAndProof aggregateAndProof);

  void sendSignedBlock(SignedBeaconBlock block);
}
