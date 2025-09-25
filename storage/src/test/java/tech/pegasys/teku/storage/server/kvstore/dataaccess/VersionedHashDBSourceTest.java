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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.SidecarIdentifier;

@SuppressWarnings("unchecked")
public class VersionedHashDBSourceTest {
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final KvStoreCombinedDao dao = mock(KvStoreCombinedDao.class);
  private final Function<BlobSidecar, VersionedHash> blobSidecarToVersionedHash =
      mock(Function.class);
  private final Function<Pair<DataColumnSidecar, UInt64>, VersionedHash>
      dataColumnSidecarToVersionedHash = mock(Function.class);
  private final VersionedHash versionedHash = dataStructureUtil.randomVersionedHash();
  private VersionedHashDBSource versionedHashDBSource =
      new VersionedHashDBSource(
          dao, blobSidecarToVersionedHash, dataColumnSidecarToVersionedHash, spec);

  @BeforeEach
  public void setup() {
    when(blobSidecarToVersionedHash.apply(any())).thenReturn(versionedHash);
    when(dataColumnSidecarToVersionedHash.apply(any())).thenReturn(versionedHash);
    versionedHashDBSource =
        new VersionedHashDBSource(
            dao, blobSidecarToVersionedHash, dataColumnSidecarToVersionedHash, spec);
  }

  @Test
  public void storeSidecarHashes_isFunctional() {
    assertThat(versionedHashDBSource.isStoreSidecarHashes()).isFalse();
    versionedHashDBSource.storeSidecarHashes();
    assertThat(versionedHashDBSource.isStoreSidecarHashes()).isTrue();
    versionedHashDBSource.storeSidecarHashes();
    // we change it once and it cannot be rolled back or whatever
    assertThat(versionedHashDBSource.isStoreSidecarHashes()).isTrue();
  }

  @Test
  public void addBlobSidecarVersionedHash_isFunctional() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    versionedHashDBSource.addBlobSidecarVersionedHash(blobSidecar, updater);
    verify(blobSidecarToVersionedHash).apply(blobSidecar);
    verify(updater).addVersionedHash(eq(versionedHash.get()), any());
  }

  @Test
  public void addNonCanonicalBlobSidecarVersionedHash_isFunctional() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    versionedHashDBSource.addNonCanonicalBlobSidecarVersionedHash(blobSidecar, updater);
    verify(blobSidecarToVersionedHash).apply(blobSidecar);
    verify(updater).addVersionedHash(eq(versionedHash.get()), any());
  }

  @Test
  public void addNonCanonicalBlobSidecarVersionedHashRaw_isFunctional() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    versionedHashDBSource.addNonCanonicalBlobSidecarVersionedHash(
        blobSidecar.sszSerialize(), updater);
    verify(blobSidecarToVersionedHash).apply(blobSidecar);
    verify(updater).addVersionedHash(eq(versionedHash.get()), any());
  }

  @Test
  public void removeBlobSidecarVersionedHash_isFunctional() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    when(dao.getBlobSidecar(blobSidecar.getSlotAndBlockRootAndBlobIndex()))
        .thenReturn(Optional.of(blobSidecar.sszSerialize()));
    versionedHashDBSource.removeBlobSidecarVersionedHash(
        blobSidecar.getSlotAndBlockRootAndBlobIndex(), updater);
    verify(blobSidecarToVersionedHash).apply(blobSidecar);
    verify(updater).removeVersionedHash(eq(versionedHash.get()));
  }

  @Test
  public void removeNonCanonicalBlobSidecarVersionedHash_isFunctional() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    when(dao.getNonCanonicalBlobSidecar(blobSidecar.getSlotAndBlockRootAndBlobIndex()))
        .thenReturn(Optional.of(blobSidecar.sszSerialize()));
    versionedHashDBSource.removeNonCanonicalBlobSidecarVersionedHash(
        blobSidecar.getSlotAndBlockRootAndBlobIndex(), updater);
    verify(blobSidecarToVersionedHash).apply(blobSidecar);
    verify(updater).removeVersionedHash(eq(versionedHash.get()));
  }

  @Test
  public void addSidecarVersionedHashes_isFunctional() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    versionedHashDBSource.addSidecarVersionedHashes(dataColumnSidecar, updater);
    // not activated yet
    verifyNoInteractions(updater);

    versionedHashDBSource.storeSidecarHashes();
    versionedHashDBSource.addSidecarVersionedHashes(dataColumnSidecar, updater);
    IntStream.range(0, dataColumnSidecar.getSszKZGCommitments().size())
        .forEach(
            index ->
                verify(dataColumnSidecarToVersionedHash)
                    .apply(Pair.of(dataColumnSidecar, UInt64.valueOf(index))));
    verify(updater, times(dataColumnSidecar.getSszKZGCommitments().size()))
        .addVersionedHash(eq(versionedHash.get()), any());

    // stored only once, ignores next sidecars from the same block
    versionedHashDBSource.addSidecarVersionedHashes(
        dataStructureUtil.randomDataColumnSidecar(
            dataColumnSidecar.getSignedBeaconBlockHeader(),
            dataColumnSidecar.getIndex().increment()),
        updater);
    verifyNoMoreInteractions(dataColumnSidecarToVersionedHash, updater);
  }

  @Test
  public void addNonCanonicalSidecarVersionedHashes_isFunctional() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    versionedHashDBSource.addSidecarVersionedHashes(dataColumnSidecar, updater);
    // not activated yet
    verifyNoInteractions(updater);

    versionedHashDBSource.storeSidecarHashes();
    versionedHashDBSource.addNonCanonicalSidecarVersionedHashes(dataColumnSidecar, updater);
    IntStream.range(0, dataColumnSidecar.getSszKZGCommitments().size())
        .forEach(
            index ->
                verify(dataColumnSidecarToVersionedHash)
                    .apply(Pair.of(dataColumnSidecar, UInt64.valueOf(index))));
    verify(updater, times(dataColumnSidecar.getSszKZGCommitments().size()))
        .addVersionedHash(eq(versionedHash.get()), any());

    // stored only once, ignores next sidecars from the same block
    versionedHashDBSource.addSidecarVersionedHashes(
        dataStructureUtil.randomDataColumnSidecar(
            dataColumnSidecar.getSignedBeaconBlockHeader(),
            dataColumnSidecar.getIndex().increment()),
        updater);
    verifyNoMoreInteractions(dataColumnSidecarToVersionedHash, updater);
  }

  @Test
  public void addNonCanonicalSidecarVersionedHashesRaw_isFunctional() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    versionedHashDBSource.addSidecarVersionedHashes(dataColumnSidecar, updater);
    // not activated yet
    verifyNoInteractions(updater);

    versionedHashDBSource.storeSidecarHashes();
    versionedHashDBSource.addNonCanonicalSidecarVersionedHashes(
        dataColumnSidecar.sszSerialize(), updater);
    IntStream.range(0, dataColumnSidecar.getSszKZGCommitments().size())
        .forEach(
            index ->
                verify(dataColumnSidecarToVersionedHash)
                    .apply(Pair.of(dataColumnSidecar, UInt64.valueOf(index))));
    verify(updater, times(dataColumnSidecar.getSszKZGCommitments().size()))
        .addVersionedHash(eq(versionedHash.get()), any());

    // stored only once, ignores next sidecars from the same block
    versionedHashDBSource.addSidecarVersionedHashes(
        dataStructureUtil.randomDataColumnSidecar(
            dataColumnSidecar.getSignedBeaconBlockHeader(),
            dataColumnSidecar.getIndex().increment()),
        updater);
    verifyNoMoreInteractions(dataColumnSidecarToVersionedHash, updater);
  }

  @Test
  public void removeSidecarVersionedHashes_isFunctional() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier =
        DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar);
    when(dao.getSidecar(dataColumnSlotAndIdentifier))
        .thenReturn(Optional.of(dataColumnSidecar.sszSerialize()));

    versionedHashDBSource.removeSidecarVersionedHashes(dataColumnSlotAndIdentifier, updater);
    // not activated yet
    verifyNoInteractions(updater, dao);

    versionedHashDBSource.storeSidecarHashes();
    versionedHashDBSource.removeSidecarVersionedHashes(dataColumnSlotAndIdentifier, updater);
    IntStream.range(0, dataColumnSidecar.getSszKZGCommitments().size())
        .forEach(
            index ->
                verify(dataColumnSidecarToVersionedHash)
                    .apply(Pair.of(dataColumnSidecar, UInt64.valueOf(index))));
    verify(dao).getSidecar(dataColumnSlotAndIdentifier);
    verify(updater, times(dataColumnSidecar.getSszKZGCommitments().size()))
        .removeVersionedHash(eq(versionedHash.get()));
  }

  @Test
  public void removeNonCanonicalSidecarVersionedHashes_isFunctional() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier =
        DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar);
    when(dao.getNonCanonicalSidecar(dataColumnSlotAndIdentifier))
        .thenReturn(Optional.of(dataColumnSidecar.sszSerialize()));

    versionedHashDBSource.removeNonCanonicalSidecarVersionedHashes(
        dataColumnSlotAndIdentifier, updater);
    // not activated yet
    verifyNoInteractions(updater, dao);

    versionedHashDBSource.storeSidecarHashes();
    versionedHashDBSource.removeNonCanonicalSidecarVersionedHashes(
        dataColumnSlotAndIdentifier, updater);
    IntStream.range(0, dataColumnSidecar.getSszKZGCommitments().size())
        .forEach(
            index ->
                verify(dataColumnSidecarToVersionedHash)
                    .apply(Pair.of(dataColumnSidecar, UInt64.valueOf(index))));
    verify(dao).getNonCanonicalSidecar(dataColumnSlotAndIdentifier);
    verify(updater, times(dataColumnSidecar.getSszKZGCommitments().size()))
        .removeVersionedHash(eq(versionedHash.get()));
  }

  @Test
  public void computeCanonicalBlobSidecarMetadata_SerializeDeSerialize() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final SlotAndBlockRootAndBlobIndex expectedBlobIdentifier =
        blobSidecar.getSlotAndBlockRootAndBlobIndex();
    final Bytes data = VersionedHashDBSource.computeCanonicalBlobSidecarMetadata(blobSidecar);
    when(dao.getSidecarIdentifierData(any())).thenReturn(Optional.of(data));

    final Optional<SidecarIdentifier> maybeSidecarIdentifier =
        versionedHashDBSource.getSidecarIdentifier(versionedHash);
    verify(dao).getSidecarIdentifierData(any());
    assertThat(maybeSidecarIdentifier.isPresent()).isTrue();
    final SidecarIdentifier sidecarIdentifier = maybeSidecarIdentifier.get();
    assertThat(sidecarIdentifier.canonical()).isTrue();
    assertThat(sidecarIdentifier.dataColumnSidecarIdentifierAndBlobIndex()).isEmpty();
    assertThat(sidecarIdentifier.blobSidecarIdentifier()).contains(expectedBlobIdentifier);
  }

  @Test
  public void computeNonCanonicalBlobSidecarMetadata_SerializeDeSerialize() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final SlotAndBlockRootAndBlobIndex expectedBlobIdentifier =
        blobSidecar.getSlotAndBlockRootAndBlobIndex();
    final Bytes data = VersionedHashDBSource.computeNonCanonicalBlobSidecarMetadata(blobSidecar);
    when(dao.getSidecarIdentifierData(any())).thenReturn(Optional.of(data));

    final Optional<SidecarIdentifier> maybeSidecarIdentifier =
        versionedHashDBSource.getSidecarIdentifier(versionedHash);
    verify(dao).getSidecarIdentifierData(any());
    assertThat(maybeSidecarIdentifier.isPresent()).isTrue();
    final SidecarIdentifier sidecarIdentifier = maybeSidecarIdentifier.get();
    assertThat(sidecarIdentifier.canonical()).isFalse();
    assertThat(sidecarIdentifier.dataColumnSidecarIdentifierAndBlobIndex()).isEmpty();
    assertThat(sidecarIdentifier.blobSidecarIdentifier()).contains(expectedBlobIdentifier);
  }

  @Test
  public void computeCanonicalDataColumnSidecarMetadata_SerializeDeSerialize() {
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    final UInt64 expectedBlobIndex = UInt64.valueOf(123);
    final Bytes data =
        VersionedHashDBSource.computeCanonicalDataColumnSidecarMetadata(
            dataColumnSidecar, expectedBlobIndex);
    when(dao.getSidecarIdentifierData(any())).thenReturn(Optional.of(data));

    final Optional<SidecarIdentifier> maybeSidecarIdentifier =
        versionedHashDBSource.getSidecarIdentifier(versionedHash);
    verify(dao).getSidecarIdentifierData(any());
    assertThat(maybeSidecarIdentifier.isPresent()).isTrue();
    final SidecarIdentifier sidecarIdentifier = maybeSidecarIdentifier.get();
    assertThat(sidecarIdentifier.canonical()).isTrue();
    assertThat(sidecarIdentifier.blobSidecarIdentifier()).isEmpty();
    assertThat(sidecarIdentifier.dataColumnSidecarIdentifierAndBlobIndex()).isPresent();
    final Pair<SlotAndBlockRoot, UInt64> dataColumnSlotAndIdentifierAndBlobIndex =
        sidecarIdentifier.dataColumnSidecarIdentifierAndBlobIndex().get();
    assertThat(dataColumnSlotAndIdentifierAndBlobIndex.getKey())
        .isEqualTo(dataColumnSidecar.getSlotAndBlockRoot());
    assertThat(dataColumnSlotAndIdentifierAndBlobIndex.getValue()).isEqualTo(expectedBlobIndex);
  }

  @Test
  public void computeNonCanonicalDataColumnSidecarMetadata_SerializeDeSerialize() {
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    final UInt64 expectedBlobIndex = UInt64.valueOf(123);
    final Bytes data =
        VersionedHashDBSource.computeNonCanonicalDataColumnSidecarMetadata(
            dataColumnSidecar, expectedBlobIndex);
    when(dao.getSidecarIdentifierData(any())).thenReturn(Optional.of(data));

    final Optional<SidecarIdentifier> maybeSidecarIdentifier =
        versionedHashDBSource.getSidecarIdentifier(versionedHash);
    verify(dao).getSidecarIdentifierData(any());
    assertThat(maybeSidecarIdentifier.isPresent()).isTrue();
    final SidecarIdentifier sidecarIdentifier = maybeSidecarIdentifier.get();
    assertThat(sidecarIdentifier.canonical()).isFalse();
    assertThat(sidecarIdentifier.blobSidecarIdentifier()).isEmpty();
    assertThat(sidecarIdentifier.dataColumnSidecarIdentifierAndBlobIndex()).isPresent();
    final Pair<SlotAndBlockRoot, UInt64> dataColumnSlotAndIdentifierAndBlobIndex =
        sidecarIdentifier.dataColumnSidecarIdentifierAndBlobIndex().get();
    assertThat(dataColumnSlotAndIdentifierAndBlobIndex.getKey())
        .isEqualTo(dataColumnSidecar.getSlotAndBlockRoot());
    assertThat(dataColumnSlotAndIdentifierAndBlobIndex.getValue()).isEqualTo(expectedBlobIndex);
  }
}
