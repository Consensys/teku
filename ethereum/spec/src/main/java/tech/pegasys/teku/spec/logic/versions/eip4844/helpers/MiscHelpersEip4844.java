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

package tech.pegasys.teku.spec.logic.versions.eip4844.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.spec.config.SpecConfigEip4844.BLOB_TX_TYPE;
import static tech.pegasys.teku.spec.config.SpecConfigEip4844.VERSIONED_HASH_VERSION_KZG;

import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.kzg.ckzg4844.CKZG4844;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.Blob;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.MiscHelpersBellatrix;
import tech.pegasys.teku.spec.logic.versions.eip4844.types.VersionedHash;

public class MiscHelpersEip4844 extends MiscHelpersBellatrix {

  private final KZG kzg;

  public MiscHelpersEip4844(final SpecConfigEip4844 specConfig) {
    super(specConfig);
    this.kzg = initKZG(specConfig);
  }

  private static KZG initKZG(final SpecConfigEip4844 config) {
    final KZG kzg;
    if (!config.getEip4844ForkEpoch().equals(SpecConfig.FAR_FUTURE_EPOCH) && !config.isKZGNoop()) {
      kzg = CKZG4844.createInstance(config.getFieldElementsPerBlob());
      kzg.loadTrustedSetup(config.getTrustedSetupPath().orElseThrow());
    } else {
      kzg = KZG.NOOP;
    }

    return kzg;
  }

  private void validateBlobSidecar(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final List<KZGCommitment> kzgCommitments,
      final BlobsSidecar blobsSidecar) {
    checkArgument(
        slot.equals(blobsSidecar.getBeaconBlockSlot()),
        "Block slot should match blobs sidecar slot");
    checkArgument(
        beaconBlockRoot.equals(blobsSidecar.getBeaconBlockRoot()),
        "Block root should match blobs sidecar beacon block root");
    checkArgument(
        kzgCommitments.size() == blobsSidecar.getBlobs().size(),
        "Number of KZG commitments should match number of blobs");
    final boolean isValidProof =
        kzg.verifyAggregateKzgProof(
            blobsSidecar.getBlobs().stream().map(Blob::getBytes).collect(Collectors.toList()),
            kzgCommitments,
            blobsSidecar.getKZGAggregatedProof());
    checkState(isValidProof, "Invalid aggregate KZG proof for the given blobs and commitments");
  }

  @Override
  public boolean isDataAvailable(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final List<KZGCommitment> kzgCommitments,
      final BlobsSidecar blobsSidecar) {
    validateBlobSidecar(slot, beaconBlockRoot, kzgCommitments, blobsSidecar);
    return true;
  }

  @VisibleForTesting
  public VersionedHash kzgCommitmentToVersionedHash(final KZGCommitment kzgCommitment) {
    return VersionedHash.create(
        VERSIONED_HASH_VERSION_KZG, Hash.sha256(kzgCommitment.getBytesCompressed()));
  }

  @VisibleForTesting
  public List<VersionedHash> txPeekBlobVersionedHashes(final Transaction transaction) {
    checkArgument(isBlobTransaction(transaction), "Transaction should be of BLOB type");
    final Bytes txData = transaction.getBytes();
    // 1st byte is transaction type, next goes ssz encoded SignedBlobTransaction
    // Getting variable length BlobTransaction offset, which is the message of signed tx
    final int messageOffset =
        UInt32.fromBytes(txData.slice(1, 4), ByteOrder.LITTLE_ENDIAN).add(1).intValue();
    // Getting blobVersionedHashes field offset in BlobTransaction
    // field offset: 32 + 8 + 32 + 32 + 8 + 4 + 32 + 4 + 4 + 32 = 188
    final int blobVersionedHashesOffset =
        messageOffset
            + UInt32.fromBytes(txData.slice(messageOffset + 188, 4), ByteOrder.LITTLE_ENDIAN)
                .intValue();
    final List<VersionedHash> versionedHashes = new ArrayList<>();
    for (int hashStartOffset = blobVersionedHashesOffset;
        hashStartOffset < txData.size();
        hashStartOffset += VersionedHash.SIZE) {
      versionedHashes.add(
          new VersionedHash(Bytes32.wrap(txData.slice(hashStartOffset, VersionedHash.SIZE))));
    }

    return versionedHashes;
  }

  private boolean isBlobTransaction(final Transaction transaction) {
    return transaction.getBytes().get(0) == BLOB_TX_TYPE.get(0);
  }

  @Override
  public boolean verifyKZGCommitmentsAgainstTransactions(
      final List<Transaction> transactions, final List<KZGCommitment> kzgCommitments) {
    final List<VersionedHash> transactionsVersionedHashes =
        transactions.stream()
            .filter(this::isBlobTransaction)
            .map(this::txPeekBlobVersionedHashes)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    final List<VersionedHash> commitmentsVersionedHashes =
        kzgCommitments.stream()
            .map(this::kzgCommitmentToVersionedHash)
            .collect(Collectors.toList());
    return transactionsVersionedHashes.equals(commitmentsVersionedHashes);
  }

  public KZGCommitment blobToKzgCommitment(final Blob blob) {
    return kzg.blobToKzgCommitment(blob.getBytes());
  }

  public KZGProof computeAggregatedKzgProof(final List<Bytes> blobs) {
    return kzg.computeAggregateKzgProof(blobs);
  }

  @Override
  public Optional<MiscHelpersEip4844> toVersionEip4844() {
    return Optional.of(this);
  }
}
