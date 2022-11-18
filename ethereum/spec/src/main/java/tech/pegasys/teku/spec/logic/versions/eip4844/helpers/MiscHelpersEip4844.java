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
import static tech.pegasys.teku.spec.config.SpecConfigEip4844.BLOB_TX_TYPE;
import static tech.pegasys.teku.spec.config.SpecConfigEip4844.VERSIONED_HASH_VERSION_KZG;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.SignedBlobTransaction;
import tech.pegasys.teku.spec.logic.versions.capella.helpers.MiscHelpersCapella;
import tech.pegasys.teku.spec.logic.versions.eip4844.util.KZGUtilEip4844;

public class MiscHelpersEip4844 extends MiscHelpersCapella {

  public MiscHelpersEip4844(final SpecConfig specConfig) {
    super(specConfig);
  }

  public void validateBlobSidecar(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final List<KZGCommitment> kzgCommitments,
      final BlobsSidecar blobsSidecar) {
    checkArgument(
        slot.equals(blobsSidecar.getBeaconBlockSlot()),
        "Block slot should match blobsSidecar slot");
    checkArgument(
        beaconBlockRoot.equals(blobsSidecar.getBeaconBlockRoot()),
        "Block root should match blobsSidecar beacon block root");
    checkArgument(
        kzgCommitments.size() == blobsSidecar.getBlobs().size(),
        "Number of kzgCommitments should match number of blobs");
    KZGUtilEip4844.verifyAggregateKZGProof(
        blobsSidecar.getBlobs(), kzgCommitments, blobsSidecar.getKZGAggregatedProof());
  }

  public boolean isDataAvailable(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final List<KZGCommitment> kzgCommitments,
      final BlobsSidecar blobsSidecar) {
    validateBlobSidecar(slot, beaconBlockRoot, kzgCommitments, blobsSidecar);
    return true;
  }

  private Bytes32 kzgCommitmentToVersionedHash(final KZGCommitment kzgCommitment) {
    return Bytes32.wrap(
        Bytes.wrap(
            VERSIONED_HASH_VERSION_KZG, Hash.sha256(kzgCommitment.getBytesCompressed()).slice(1)));
  }

  private List<Bytes32> txPeekBlobVersionedHashes(final Transaction transaction) {
    checkArgument(isBlobTransaction(transaction), "Transaction should be of BLOB type");
    final SignedBlobTransaction signedBlobTransaction =
        SignedBlobTransaction.SSZ_SCHEMA.sszDeserialize(transaction.getBytes().slice(1));
    return signedBlobTransaction.getBlobTransaction().getBlobVersionedHashes();
  }

  private boolean isBlobTransaction(final Transaction transaction) {
    return transaction.getBytes().get(0) == BLOB_TX_TYPE.get(0);
  }

  public boolean verifyKZGCommitmentsAgainstTransactions(
      final List<Transaction> transactions, final List<KZGCommitment> kzgCommitments) {
    final List<Bytes32> transactionsVersionedHashes =
        transactions.stream()
            .filter(this::isBlobTransaction)
            .map(this::txPeekBlobVersionedHashes)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    final List<Bytes32> commitmentsVersionedHashes =
        kzgCommitments.stream()
            .map(this::kzgCommitmentToVersionedHash)
            .collect(Collectors.toList());
    return transactionsVersionedHashes.equals(commitmentsVersionedHashes);
  }
}
