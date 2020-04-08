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

package tech.pegasys.artemis.validator.api;

import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.util.async.SafeFuture;

public interface ValidatorApiChannel {
  SafeFuture<Optional<Fork>> getFork();

  SafeFuture<Optional<List<ValidatorDuties>>> getDuties(
      UnsignedLong epoch, Collection<BLSPublicKey> publicKeys);

  SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      UnsignedLong slot, BLSSignature randaoReveal);

  SafeFuture<Optional<Attestation>> createUnsignedAttestation(
      UnsignedLong slot, int committeeIndex);

  SafeFuture<Optional<Attestation>> createAggregate(AttestationData attestationData);

  void subscribeToBeaconCommittee(int committeeIndex, UnsignedLong aggregationSlot);

  void sendSignedAttestation(Attestation attestation);

  void sendAggregateAndProof(AggregateAndProof aggregateAndProof);

  void sendSignedBlock(SignedBeaconBlock block);
}
