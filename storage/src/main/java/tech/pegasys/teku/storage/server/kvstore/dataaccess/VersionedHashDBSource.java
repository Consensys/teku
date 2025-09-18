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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.storage.server.kvstore.serialization.UInt64Serializer;

public class VersionedHashDBSource {

  private static final Logger LOG = LogManager.getLogger();

  private final Bytes CANONICAL_BLOB_SIDECAR_TYPE = Bytes.of(0x00);
  private final Bytes NON_CANONICAL_BLOB_SIDECAR_TYPE = Bytes.of(0x10);
  private final Bytes CANONICAL_DATA_COLUMN_SIDECAR_TYPE = Bytes.of(0x20);
  private final Bytes NON_CANONICAL_DATA_COLUMN_SIDECAR_TYPE = Bytes.of(0x30);

  static final int TYPE_SIZE = 1;
  static final int SLOT_SIZE = Long.BYTES;
  static final int BLOCK_ROOT_SIZE = Bytes32.SIZE;
  static final int BLOB_INDEX_SIZE = Long.BYTES;

  static final int SLOT_OFFSET = TYPE_SIZE;
  static final int BLOCK_ROOT_OFFSET = SLOT_OFFSET + SLOT_SIZE;
  static final int BLOB_INDEX_OFFSET = BLOCK_ROOT_OFFSET + BLOCK_ROOT_SIZE;
  static final int DATA_SIZE = BLOB_INDEX_OFFSET + BLOB_INDEX_SIZE;

  private final Function<BlobSidecar, VersionedHash> blobSidecarToVersionedHash;
  private final Function<Pair<DataColumnSidecar, UInt64>, VersionedHash>
      dataColumnSidecarToVersionedHash;
  private final AtomicBoolean storeSidecarHashes = new AtomicBoolean(false);
  private final Supplier<SchemaDefinitionsDeneb> schemaDefinitionsDeneb;
  private final Supplier<SchemaDefinitionsFulu> schemaDefinitionsFulu;
  private final KvStoreCombinedDao dao;
  private final Set<SlotAndBlockRoot> filledSidecarSlots;

  public VersionedHashDBSource(
      final KvStoreCombinedDao dao,
      final Function<BlobSidecar, VersionedHash> blobSidecarToVersionedHash,
      final Function<Pair<DataColumnSidecar, UInt64>, VersionedHash>
          dataColumnSidecarToVersionedHash,
      final Spec spec) {
    this.dao = dao;
    this.blobSidecarToVersionedHash = blobSidecarToVersionedHash;
    this.dataColumnSidecarToVersionedHash = dataColumnSidecarToVersionedHash;
    this.schemaDefinitionsDeneb =
        () ->
            SchemaDefinitionsDeneb.required(
                spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions());
    this.schemaDefinitionsFulu =
        () ->
            SchemaDefinitionsFulu.required(
                spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
    this.filledSidecarSlots = LimitedSet.createSynchronized(1000);
  }

  public void storeSidecarHashes() {
    this.storeSidecarHashes.set(true);
    LOG.debug("Starting storing DataColumnSidecars VersionedHashes");
  }

  public void addBlobSidecarVersionedHash(
      final BlobSidecar blobSidecar, final KvStoreCombinedDao.FinalizedUpdater updater) {
    LOG.trace("Adding BlobSidecar versioned hash {}", blobSidecar::toLogString);
    updater.addVersionedHash(
        blobSidecarToVersionedHash.apply(blobSidecar).get(),
        computeCanonicalBlobSidecarMetadata(blobSidecar));
  }

  public void addNonCanonicalBlobSidecarVersionedHash(
      final BlobSidecar blobSidecar, final KvStoreCombinedDao.FinalizedUpdater updater) {
    LOG.trace("Adding non-canonical BlobSidecar versioned hash {}", blobSidecar::toLogString);
    updater.addVersionedHash(
        blobSidecarToVersionedHash.apply(blobSidecar).get(),
        computeNonCanonicalBlobSidecarMetadata(blobSidecar));
  }

  public void addNonCanonicalBlobSidecarVersionedHash(
      final Bytes blobSidecarRaw, final KvStoreCombinedDao.FinalizedUpdater updater) {
    addNonCanonicalBlobSidecarVersionedHash(
        schemaDefinitionsDeneb.get().getBlobSidecarSchema().sszDeserialize(blobSidecarRaw),
        updater);
  }

  /** Run before removal of BlobSidecar, requires BlobSidecar */
  public void removeBlobSidecarVersionedHash(
      final SlotAndBlockRootAndBlobIndex key, final KvStoreCombinedDao.FinalizedUpdater updater) {
    final Optional<Bytes> maybeBlobSidecarRaw = dao.getBlobSidecar(key);
    if (maybeBlobSidecarRaw.isPresent()) {
      final BlobSidecar blobSidecar =
          schemaDefinitionsDeneb
              .get()
              .getBlobSidecarSchema()
              .sszDeserialize(maybeBlobSidecarRaw.get());
      LOG.trace("Removing BlobSidecar versioned hash {}", blobSidecar::toLogString);
      updater.removeVersionedHash(blobSidecarToVersionedHash.apply(blobSidecar).get());
    }
  }

  /** Run before removal of BlobSidecar, requires BlobSidecar */
  public void removeNonCanonicalBlobSidecarVersionedHash(
      final SlotAndBlockRootAndBlobIndex key, final KvStoreCombinedDao.FinalizedUpdater updater) {
    final Optional<Bytes> maybeNonCanonicalBlobSidecarRaw = dao.getNonCanonicalBlobSidecar(key);
    if (maybeNonCanonicalBlobSidecarRaw.isPresent()) {
      final BlobSidecar blobSidecar =
          schemaDefinitionsDeneb
              .get()
              .getBlobSidecarSchema()
              .sszDeserialize(maybeNonCanonicalBlobSidecarRaw.get());
      LOG.trace("Removing non-canonical BlobSidecar versioned hash {}", blobSidecar::toLogString);
      updater.removeVersionedHash(blobSidecarToVersionedHash.apply(blobSidecar).get());
    }
  }

  /** Call before storing sidecar */
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
      for (int i = 0; i < sidecar.getSszKZGCommitments().size(); ++i) {
        updater.addVersionedHash(
            dataColumnSidecarToVersionedHash.apply(Pair.of(sidecar, UInt64.valueOf(i))).get(),
            computeCanonicalDataColumnSidecarMetadata(sidecar, UInt64.valueOf(i)));
      }
    }
  }

  /** Call before storing sidecar */
  public void addNonCanonicalSidecarVersionedHashes(
      final Bytes sidecar, final KvStoreCombinedDao.FinalizedUpdater updater) {
    addNonCanonicalSidecarVersionedHashes(
        schemaDefinitionsFulu.get().getDataColumnSidecarSchema().sszDeserialize(sidecar), updater);
  }

  /** Call before storing sidecar */
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
      for (int i = 0; i < sidecar.getSszKZGCommitments().size(); ++i) {
        updater.addVersionedHash(
            dataColumnSidecarToVersionedHash.apply(Pair.of(sidecar, UInt64.valueOf(i))).get(),
            computeNonCanonicalDataColumnSidecarMetadata(sidecar, UInt64.valueOf(i)));
      }
    }
  }

  public void removeSidecarVersionedHashes(
      final DataColumnSlotAndIdentifier someKey,
      final KvStoreCombinedDao.FinalizedUpdater updater) {
    if (!storeSidecarHashes.get()) {
      return;
    }
    final Optional<Bytes> maybeSidecarRaw = dao.getSidecar(someKey);
    if (maybeSidecarRaw.isPresent()) {
      final DataColumnSidecar sidecar =
          schemaDefinitionsFulu
              .get()
              .getDataColumnSidecarSchema()
              .sszDeserialize(maybeSidecarRaw.get());
      LOG.trace("Removing DataColumnSidecar versioned hashes {}", sidecar::toLogString);
      for (int i = 0; i < sidecar.getSszKZGCommitments().size(); ++i) {
        updater.removeVersionedHash(
            dataColumnSidecarToVersionedHash.apply(Pair.of(sidecar, UInt64.valueOf(i))).get());
      }
    }
  }

  public void removeNonCanonicalSidecarVersionedHashes(
      final DataColumnSlotAndIdentifier someKey,
      final KvStoreCombinedDao.FinalizedUpdater updater) {
    if (!storeSidecarHashes.get()) {
      return;
    }
    final Optional<Bytes> maybeSidecarRaw = dao.getNonCanonicalSidecar(someKey);
    if (maybeSidecarRaw.isPresent()) {
      final DataColumnSidecar sidecar =
          schemaDefinitionsFulu
              .get()
              .getDataColumnSidecarSchema()
              .sszDeserialize(maybeSidecarRaw.get());
      LOG.trace(
          "Removing non-canonical DataColumnSidecar versioned hashes {}", sidecar::toLogString);
      for (int i = 0; i < sidecar.getSszKZGCommitments().size(); ++i) {
        updater.removeVersionedHash(
            dataColumnSidecarToVersionedHash.apply(Pair.of(sidecar, UInt64.valueOf(i))).get());
      }
    }
  }

  public Optional<Sidecar> getSidecarIdentifier(final VersionedHash hash) {
    final Optional<Bytes> maybeSidecarIdentifierData = dao.getSidecarIdentifierData(hash);
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
              new Sidecar(
                  Optional.of(new SlotAndBlockRootAndBlobIndex(slot, blockRoot, blobIndex)),
                  Optional.empty(),
                  true));
      case 0x10 ->
          Optional.of(
              new Sidecar(
                  Optional.of(new SlotAndBlockRootAndBlobIndex(slot, blockRoot, blobIndex)),
                  Optional.empty(),
                  false));
      case 0x20 ->
          Optional.of(
              new Sidecar(
                  Optional.empty(),
                  Optional.of(
                      Pair.of(
                          new DataColumnSlotAndIdentifier(slot, blockRoot, UInt64.ZERO),
                          blobIndex)),
                  true));
      case 0x30 ->
          Optional.of(
              new Sidecar(
                  Optional.empty(),
                  Optional.of(
                      Pair.of(
                          new DataColumnSlotAndIdentifier(slot, blockRoot, UInt64.ZERO),
                          blobIndex)),
                  false));
      default -> throw new IllegalStateException("Unexpected encoded data: " + data.toHexString());
    };
  }

  public record Sidecar(
      Optional<SlotAndBlockRootAndBlobIndex> blobSidecarIdentifier,
      Optional<Pair<DataColumnSlotAndIdentifier, UInt64>> dataColumnSidecarIdentifierAndBlobIndex,
      boolean canonical) {
    public Sidecar {
      if (blobSidecarIdentifier.isPresent()
          == dataColumnSidecarIdentifierAndBlobIndex.isPresent()) {
        throw new RuntimeException(
            "Either blob sidecar or data column sidecars identifier have to be set");
      }
    }

    boolean isBlobSidecar() {
      return blobSidecarIdentifier.isPresent();
    }

    boolean isDataColumnSidecars() {
      return dataColumnSidecarIdentifierAndBlobIndex.isPresent();
    }
  }

  private Bytes computeCanonicalBlobSidecarMetadata(final BlobSidecar blobSidecar) {
    return Bytes.concatenate(CANONICAL_BLOB_SIDECAR_TYPE, computeBlobSidecarMetadata(blobSidecar));
  }

  private Bytes computeNonCanonicalBlobSidecarMetadata(final BlobSidecar blobSidecar) {
    return Bytes.concatenate(
        NON_CANONICAL_BLOB_SIDECAR_TYPE, computeBlobSidecarMetadata(blobSidecar));
  }

  private Bytes computeCanonicalDataColumnSidecarMetadata(
      final DataColumnSidecar sidecar, final UInt64 blobIndex) {
    return Bytes.concatenate(
        CANONICAL_DATA_COLUMN_SIDECAR_TYPE, computeDataColumnSidecarMetadata(sidecar, blobIndex));
  }

  private Bytes computeNonCanonicalDataColumnSidecarMetadata(
      final DataColumnSidecar sidecar, final UInt64 blobIndex) {
    return Bytes.concatenate(
        NON_CANONICAL_DATA_COLUMN_SIDECAR_TYPE,
        computeDataColumnSidecarMetadata(sidecar, blobIndex));
  }

  private Bytes computeBlobSidecarMetadata(final BlobSidecar blobSidecar) {
    return Bytes.concatenate(
        Bytes.wrap(Longs.toByteArray(blobSidecar.getSlot().longValue())),
        blobSidecar.getBlockRoot(),
        Bytes.wrap(Longs.toByteArray(blobSidecar.getIndex().longValue())));
  }

  private Bytes computeDataColumnSidecarMetadata(
      final DataColumnSidecar sidecar, final UInt64 blobIndex) {
    return Bytes.concatenate(
        Bytes.wrap(Longs.toByteArray(sidecar.getSlot().longValue())),
        sidecar.getBlockRoot(),
        Bytes.wrap(Longs.toByteArray(blobIndex.longValue())));
  }
}
