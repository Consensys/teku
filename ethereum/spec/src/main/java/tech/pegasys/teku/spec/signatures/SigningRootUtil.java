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

package tech.pegasys.teku.spec.signatures;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class SigningRootUtil {
  private final Spec spec;

  public SigningRootUtil(final Spec spec) {
    this.spec = spec;
  }

  public Bytes signingRootForRandaoReveal(final UInt64 epoch, final ForkInfo forkInfo) {
    final SpecVersion specVersion = spec.atEpoch(epoch);
    Bytes32 domain =
        spec.getDomain(
            Domain.RANDAO, epoch, forkInfo.getFork(), forkInfo.getGenesisValidatorsRoot());
    return specVersion.miscHelpers().computeSigningRoot(epoch, domain);
  }

  public Bytes signingRootForSignBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    return spec.computeSigningRoot(block, getDomainForSignBlock(block.getSlot(), forkInfo));
  }

  public Bytes signingRootForBlobSidecar(final BlobSidecar blobSidecar, final ForkInfo forkInfo) {
    final SpecVersion specVersion = spec.atSlot(blobSidecar.getSlot());
    final Bytes32 domain =
        spec.getDomain(
            Domain.DOMAIN_BLOB_SIDECAR,
            spec.computeEpochAtSlot(blobSidecar.getSlot()),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    specVersion.miscHelpers().computeSigningRoot(blobSidecar, domain);
    return spec.computeSigningRoot(blobSidecar, domain);
  }

  public Bytes signingRootForSignBlockHeader(
      final BeaconBlockHeader blockHeader, final ForkInfo forkInfo) {
    return spec.computeSigningRoot(
        blockHeader, getDomainForSignBlock(blockHeader.getSlot(), forkInfo));
  }

  private Bytes32 getDomainForSignBlock(UInt64 slot, ForkInfo forkInfo) {
    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_PROPOSER,
            spec.computeEpochAtSlot(slot),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return domain;
  }

  public Bytes signingRootForSignAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    final SpecVersion specVersion = spec.atSlot(attestationData.getSlot());
    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_ATTESTER,
            attestationData.getTarget().getEpoch(),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return specVersion.miscHelpers().computeSigningRoot(attestationData, domain);
  }

  public Bytes signingRootForSignAggregationSlot(final UInt64 slot, final ForkInfo forkInfo) {
    final SpecVersion specVersion = spec.atSlot(slot);
    final Bytes32 domain =
        spec.getDomain(
            Domain.SELECTION_PROOF,
            spec.computeEpochAtSlot(slot),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return specVersion.miscHelpers().computeSigningRoot(slot, domain);
  }

  public Bytes signingRootForSignAggregateAndProof(
      final AggregateAndProof aggregateAndProof, final ForkInfo forkInfo) {
    final UInt64 slot = aggregateAndProof.getAggregate().getData().getSlot();
    final SpecVersion specVersion = spec.atSlot(slot);
    final Bytes32 domain =
        spec.getDomain(
            Domain.AGGREGATE_AND_PROOF,
            spec.computeEpochAtSlot(slot),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return specVersion.miscHelpers().computeSigningRoot(aggregateAndProof, domain);
  }

  public Bytes signingRootForSignVoluntaryExit(
      final VoluntaryExit voluntaryExit, final ForkInfo forkInfo) {
    final SpecVersion specVersion = spec.atEpoch(voluntaryExit.getEpoch());
    final Bytes32 domain =
        spec.getDomain(
            Domain.VOLUNTARY_EXIT,
            voluntaryExit.getEpoch(),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return specVersion.miscHelpers().computeSigningRoot(voluntaryExit, domain);
  }

  public Bytes signingRootForValidatorRegistration(
      final ValidatorRegistration validatorRegistration) {
    final MiscHelpers miscHelpers = spec.getGenesisSpec().miscHelpers();
    final Bytes32 domain = miscHelpers.computeDomain(Domain.APPLICATION_BUILDER);
    return miscHelpers.computeSigningRoot(validatorRegistration, domain);
  }
}
