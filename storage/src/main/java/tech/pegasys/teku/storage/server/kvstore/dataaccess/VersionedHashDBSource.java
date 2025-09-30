/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Longs;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.storage.api.SidecarIdentifier;
import tech.pegasys.teku.storage.server.kvstore.serialization.UInt64Serializer;

public class VersionedHashDBSource {

  private static final Logger LOG = LogManager.getLogger();

  private static final Bytes CANONICAL_BLOB_SIDECAR_TYPE = Bytes.of(0x00);
  private static final Bytes NON_CANONICAL_BLOB_SIDECAR_TYPE = Bytes.of(0x10);
  private static final Bytes CANONICAL_DATA_COLUMN_SIDECAR_TYPE = Bytes.of(0x20);
  private static final Bytes NON_CANONICAL_DATA_COLUMN_SIDECAR_TYPE = Bytes.of(0x30);

  private static final int TYPE_SIZE = 1;
  private static final int SLOT_SIZE = Long.BYTES;
  private static final int BLOCK_ROOT_SIZE = Bytes32.SIZE;
  private static final int BLOB_INDEX_SIZE = Long.BYTES;

  private static final int SLOT_OFFSET = TYPE_SIZE;
  private static final int BLOCK_ROOT_OFFSET = SLOT_OFFSET + SLOT_SIZE;
  private static final int BLOB_INDEX_OFFSET = BLOCK_ROOT_OFFSET + BLOCK_ROOT_SIZE;
  private static final int DATA_SIZE = BLOB_INDEX_OFFSET + BLOB_INDEX_SIZE;

  private final Function<KZGCommitment, VersionedHash> kzgCommitmentToVersionedHash;
  private final AtomicBoolean storeSidecarHashes = new AtomicBoolean(false);
  private final Supplier<SchemaDefinitionsDeneb> schemaDefinitionsDeneb;
  private final KvStoreCombinedDao dao;
  private final Set<SlotAndBlockRoot> filledSidecarSlots;

  public VersionedHashDBSource(
      final KvStoreCombinedDao dao,
      final Function<KZGCommitment, VersionedHash> kzgCommitmentToVersionedHash,
      final Spec spec) {
    this.dao = dao;
    this.kzgCommitmentToVersionedHash = kzgCommitmentToVersionedHash;
    this.schemaDefinitionsDeneb =
        () ->
            SchemaDefinitionsDeneb.required(
                spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions());
    this.filledSidecarSlots = LimitedSet.createSynchronized(1000);
  }

  public void storeSidecarHashes() {
    this.storeSidecarHashes.set(true);
    LOG.debug("Starting storing DataColumnSidecars VersionedHashes");
  }

  public void addBlobSidecarVersionedHash(
      final BlobSidecar blobSidecar, final KvStoreCombinedDao.FinalizedUpdater updater) {
    final Bytes32 versionedHashBytes =
        kzgCommitmentToVersionedHash.apply(blobSidecar.getKZGCommitment()).get();
    LOG.trace(
        "Adding BlobSidecar versioned hash {}: {}",
        versionedHashBytes::toHexString,
        blobSidecar::toLogString);
    updater.addVersionedHash(versionedHashBytes, computeCanonicalBlobSidecarMetadata(blobSidecar));
  }

  public void addNonCanonicalBlobSidecarVersionedHash(
      final BlobSidecar blobSidecar, final KvStoreCombinedDao.FinalizedUpdater updater) {
    final Bytes32 versionedHashBytes =
        kzgCommitmentToVersionedHash.apply(blobSidecar.getKZGCommitment()).get();
    LOG.trace(
        "Adding non-canonical BlobSidecar versioned hash {}: {}",
        versionedHashBytes::toHexString,
        blobSidecar::toLogString);
    updater.addVersionedHash(
        versionedHashBytes, computeNonCanonicalBlobSidecarMetadata(blobSidecar));
  }

  public void addNonCanonicalBlobSidecarVersionedHash(
      final Bytes blobSidecarRaw, final KvStoreCombinedDao.FinalizedUpdater updater) {
    addNonCanonicalBlobSidecarVersionedHash(
        schemaDefinitionsDeneb.get().getBlobSidecarSchema().sszDeserialize(blobSidecarRaw),
        updater);
  }

  public void removeBlobSidecarVersionedHash(
      final Bytes blobSidecarRaw, final KvStoreCombinedDao.FinalizedUpdater updater) {
    final BlobSidecar blobSidecar =
        schemaDefinitionsDeneb.get().getBlobSidecarSchema().sszDeserialize(blobSidecarRaw);
    final Bytes32 versionedHashBytes =
        kzgCommitmentToVersionedHash.apply(blobSidecar.getKZGCommitment()).get();
    LOG.trace(
        "Removing BlobSidecar versioned hash {}: {}",
        versionedHashBytes::toHexString,
        blobSidecar::toLogString);
    updater.removeVersionedHash(versionedHashBytes);
  }

  public void removeNonCanonicalBlobSidecarVersionedHash(
      final Bytes blobSidecarRaw, final KvStoreCombinedDao.FinalizedUpdater updater) {
    final BlobSidecar blobSidecar =
        schemaDefinitionsDeneb.get().getBlobSidecarSchema().sszDeserialize(blobSidecarRaw);
    final Bytes32 versionedHashBytes =
        kzgCommitmentToVersionedHash.apply(blobSidecar.getKZGCommitment()).get();
    LOG.trace(
        "Removing non-canonical BlobSidecar versioned hash {}: {}",
        versionedHashBytes::toHexString,
        blobSidecar::toLogString);
    updater.removeVersionedHash(versionedHashBytes);
  }

  /**
   * Every DataColumnSidecar contains information about all blobs in the block, so this method acts
   * only once and ignores subsequent calls
   *
   * <p>NOTE: Call before storing sidecar. The method will not act if any sidecars for this
   * slotAndBlockRoot are saved.
   */
  public void addSidecarVersionedHashes(
      final DataColumnSidecar sidecar, final KvStoreCombinedDao.FinalizedUpdater updater) {
    if (!storeSidecarHashes.get()) {
      return;
    }
    if (filledSidecarSlots.contains(sidecar.getSlotAndBlockRoot())) {
      return;
    }

    final boolean addVersionedHashes =
        dao.getDataColumnIdentifiers(sidecar.getSlotAndBlockRoot()).isEmpty();
    if (addVersionedHashes) {
      // we don't care race much. we'll just write the same data twice
      filledSidecarSlots.add(sidecar.getSlotAndBlockRoot());
      LOG.trace("Adding DataColumnSidecar versioned hashes {}", sidecar::toLogString);
      for (int i = 0; i < sidecar.getKzgCommitments().size(); ++i) {
        updater.addVersionedHash(
            kzgCommitmentToVersionedHash
                .apply(sidecar.getKzgCommitments().get(i).getKZGCommitment())
                .get(),
            computeCanonicalDataColumnSidecarMetadata(
                sidecar.getSlotAndBlockRoot(), UInt64.valueOf(i)));
      }
    }
  }

  /**
   * Every DataColumnSidecar contains information about all blobs in the block, so this method acts
   * only once and ignores subsequent calls
   *
   * <p>NOTE: Call before storing sidecar. The method will not act if any sidecars for this
   * slotAndBlockRoot are saved.
   */
  public void addNonCanonicalSidecarVersionedHashes(
      final DataColumnSidecar sidecar, final KvStoreCombinedDao.FinalizedUpdater updater) {
    if (!storeSidecarHashes.get()) {
      return;
    }
    if (filledSidecarSlots.contains(sidecar.getSlotAndBlockRoot())) {
      return;
    }

    final boolean addVersionedHashes =
        dao.getNonCanonicalDataColumnIdentifiers(sidecar.getSlotAndBlockRoot()).isEmpty();
    if (addVersionedHashes) {
      // we don't care race much. we'll just write the same data twice
      filledSidecarSlots.add(sidecar.getSlotAndBlockRoot());
      LOG.trace("Adding non-canonical DataColumnSidecar versioned hashes {}", sidecar::toLogString);
      for (int i = 0; i < sidecar.getKzgCommitments().size(); ++i) {
        updater.addVersionedHash(
            kzgCommitmentToVersionedHash
                .apply(sidecar.getKzgCommitments().get(i).getKZGCommitment())
                .get(),
            computeNonCanonicalDataColumnSidecarMetadata(
                sidecar.getSlotAndBlockRoot(), UInt64.valueOf(i)));
      }
    }
  }

  public void removeSidecarVersionedHashes(
      final SszList<SszKZGCommitment> kzgCommitments,
      final KvStoreCombinedDao.FinalizedUpdater updater) {
    if (!storeSidecarHashes.get()) {
      return;
    }
    LOG.trace("Removing DataColumnSidecar versioned hashes for commitments: {}", kzgCommitments);
    for (SszKZGCommitment sszKZGCommitment : kzgCommitments) {
      updater.removeVersionedHash(
          kzgCommitmentToVersionedHash.apply(sszKZGCommitment.getKZGCommitment()).get());
    }
  }

  public Optional<SidecarIdentifier> getSidecarIdentifier(final VersionedHash hash) {
    final Optional<Bytes> maybeSidecarIdentifierData = dao.getSidecarIdentifierData(hash.get());
    if (maybeSidecarIdentifierData.isEmpty()) {
      return Optional.empty();
    }

    Bytes data = maybeSidecarIdentifierData.get();
    checkArgument(data.size() == DATA_SIZE);
    final UInt64 slot = UInt64Serializer.deserialize(data.toArray(), SLOT_OFFSET);
    final Bytes32 blockRoot = Bytes32.wrap(data, BLOCK_ROOT_OFFSET);
    final UInt64 blobIndex = UInt64Serializer.deserialize(data.toArray(), BLOB_INDEX_OFFSET);
    return switch (data.get(0)) {
      case 0x00 ->
          Optional.of(
              new SidecarIdentifier(
                  Optional.of(new SlotAndBlockRootAndBlobIndex(slot, blockRoot, blobIndex)),
                  Optional.empty(),
                  true));
      case 0x10 ->
          Optional.of(
              new SidecarIdentifier(
                  Optional.of(new SlotAndBlockRootAndBlobIndex(slot, blockRoot, blobIndex)),
                  Optional.empty(),
                  false));
      case 0x20 ->
          Optional.of(
              new SidecarIdentifier(
                  Optional.empty(),
                  Optional.of(Pair.of(new SlotAndBlockRoot(slot, blockRoot), blobIndex)),
                  true));
      case 0x30 ->
          Optional.of(
              new SidecarIdentifier(
                  Optional.empty(),
                  Optional.of(Pair.of(new SlotAndBlockRoot(slot, blockRoot), blobIndex)),
                  false));
      default -> throw new IllegalStateException("Unexpected encoded data: " + data.toHexString());
    };
  }

  @VisibleForTesting
  static Bytes computeCanonicalBlobSidecarMetadata(final BlobSidecar blobSidecar) {
    return Bytes.concatenate(
        CANONICAL_BLOB_SIDECAR_TYPE, computeBlobSidecarIdentifierPartial(blobSidecar));
  }

  @VisibleForTesting
  static Bytes computeNonCanonicalBlobSidecarMetadata(final BlobSidecar blobSidecar) {
    return Bytes.concatenate(
        NON_CANONICAL_BLOB_SIDECAR_TYPE, computeBlobSidecarIdentifierPartial(blobSidecar));
  }

  @VisibleForTesting
  static Bytes computeCanonicalDataColumnSidecarMetadata(
      final SlotAndBlockRoot slotAndBlockRoot, final UInt64 blobIndex) {
    return Bytes.concatenate(
        CANONICAL_DATA_COLUMN_SIDECAR_TYPE,
        computeDataColumnSidecarIdentifierPartial(slotAndBlockRoot, blobIndex));
  }

  @VisibleForTesting
  static Bytes computeNonCanonicalDataColumnSidecarMetadata(
      final SlotAndBlockRoot slotAndBlockRoot, final UInt64 blobIndex) {
    return Bytes.concatenate(
        NON_CANONICAL_DATA_COLUMN_SIDECAR_TYPE,
        computeDataColumnSidecarIdentifierPartial(slotAndBlockRoot, blobIndex));
  }

  public boolean isStoreSidecarHashes() {
    return storeSidecarHashes.get();
  }

  private static Bytes computeBlobSidecarIdentifierPartial(final BlobSidecar blobSidecar) {
    return Bytes.concatenate(
        Bytes.wrap(Longs.toByteArray(blobSidecar.getSlot().longValue())),
        blobSidecar.getBlockRoot(),
        Bytes.wrap(Longs.toByteArray(blobSidecar.getIndex().longValue())));
  }

  private static Bytes computeDataColumnSidecarIdentifierPartial(
      final SlotAndBlockRoot slotAndBlockRoot, final UInt64 blobIndex) {
    return Bytes.concatenate(
        Bytes.wrap(Longs.toByteArray(slotAndBlockRoot.getSlot().longValue())),
        slotAndBlockRoot.getBlockRoot(),
        Bytes.wrap(Longs.toByteArray(blobIndex.longValue())));
  }
}
