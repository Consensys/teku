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
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
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
  private final Function<KZGCommitment, VersionedHash> commitmentToVersionedHashFunction =
      mock(Function.class);
  private final VersionedHash versionedHash = dataStructureUtil.randomVersionedHash();
  private VersionedHashDBSource versionedHashDBSource =
      new VersionedHashDBSource(dao, commitmentToVersionedHashFunction, spec);

  @BeforeEach
  public void setup() {
    when(commitmentToVersionedHashFunction.apply(any())).thenReturn(versionedHash);
    versionedHashDBSource = new VersionedHashDBSource(dao, commitmentToVersionedHashFunction, spec);
  }

  @Test
  public void storeSidecarHashes() {
    assertThat(versionedHashDBSource.isStoreSidecarHashes()).isFalse();
    versionedHashDBSource.storeSidecarHashes();
    assertThat(versionedHashDBSource.isStoreSidecarHashes()).isTrue();
    versionedHashDBSource.storeSidecarHashes();
    // we change it once and it cannot be rolled back or whatever
    assertThat(versionedHashDBSource.isStoreSidecarHashes()).isTrue();
  }

  @Test
  public void addBlobSidecarVersionedHash() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    versionedHashDBSource.addBlobSidecarVersionedHash(blobSidecar, updater);
    verify(commitmentToVersionedHashFunction).apply(blobSidecar.getKZGCommitment());
    verify(updater).addVersionedHash(eq(versionedHash.get()), any());
    verifyNoMoreInteractions(dao, updater, commitmentToVersionedHashFunction);
  }

  @Test
  public void addNonCanonicalBlobSidecarVersionedHash() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    versionedHashDBSource.addNonCanonicalBlobSidecarVersionedHash(blobSidecar, updater);
    verify(commitmentToVersionedHashFunction).apply(blobSidecar.getKZGCommitment());
    verify(updater).addVersionedHash(eq(versionedHash.get()), any());
    verifyNoMoreInteractions(dao, updater, commitmentToVersionedHashFunction);
  }

  @Test
  public void addNonCanonicalBlobSidecarVersionedHashRaw() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    versionedHashDBSource.addNonCanonicalBlobSidecarVersionedHash(
        blobSidecar.sszSerialize(), updater);
    verify(commitmentToVersionedHashFunction).apply(blobSidecar.getKZGCommitment());
    verify(updater).addVersionedHash(eq(versionedHash.get()), any());
    verifyNoMoreInteractions(dao, updater, commitmentToVersionedHashFunction);
  }

  @Test
  public void removeBlobSidecarVersionedHash() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    when(dao.getBlobSidecar(blobSidecar.getSlotAndBlockRootAndBlobIndex()))
        .thenReturn(Optional.of(blobSidecar.sszSerialize()));
    versionedHashDBSource.removeBlobSidecarVersionedHash(blobSidecar.sszSerialize(), updater);
    verify(commitmentToVersionedHashFunction).apply(blobSidecar.getKZGCommitment());
    verify(updater).removeVersionedHash(eq(versionedHash.get()));
    verifyNoMoreInteractions(dao, updater, commitmentToVersionedHashFunction);
  }

  @Test
  public void removeNonCanonicalBlobSidecarVersionedHash() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    when(dao.getNonCanonicalBlobSidecar(blobSidecar.getSlotAndBlockRootAndBlobIndex()))
        .thenReturn(Optional.of(blobSidecar.sszSerialize()));
    versionedHashDBSource.removeNonCanonicalBlobSidecarVersionedHash(
        blobSidecar.sszSerialize(), updater);
    verify(commitmentToVersionedHashFunction).apply(blobSidecar.getKZGCommitment());
    verify(updater).removeVersionedHash(eq(versionedHash.get()));
    verifyNoMoreInteractions(dao, updater, commitmentToVersionedHashFunction);
  }

  @Test
  public void addSidecarVersionedHashes() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    versionedHashDBSource.addSidecarVersionedHashes(dataColumnSidecar, updater);
    // not activated yet
    verifyNoInteractions(updater);

    versionedHashDBSource.storeSidecarHashes();
    versionedHashDBSource.addSidecarVersionedHashes(dataColumnSidecar, updater);
    IntStream.range(0, dataColumnSidecar.getKzgCommitments().size())
        .forEach(
            index ->
                verify(commitmentToVersionedHashFunction)
                    .apply(dataColumnSidecar.getKzgCommitments().get(index).getKZGCommitment()));
    verify(updater, times(dataColumnSidecar.getKzgCommitments().size()))
        .addVersionedHash(eq(versionedHash.get()), any());
    verify(dao).getDataColumnIdentifiers(eq(dataColumnSidecar.getSlotAndBlockRoot()));

    // stored only once, ignores next sidecars from the same block
    versionedHashDBSource.addSidecarVersionedHashes(
        dataStructureUtil.randomDataColumnSidecar(
            DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader(),
            dataColumnSidecar.getIndex().increment()),
        updater);
    verifyNoMoreInteractions(dao, updater, commitmentToVersionedHashFunction);
  }

  @Test
  public void addNonCanonicalSidecarVersionedHashes() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    versionedHashDBSource.addSidecarVersionedHashes(dataColumnSidecar, updater);
    // not activated yet
    verifyNoInteractions(updater);

    versionedHashDBSource.storeSidecarHashes();
    versionedHashDBSource.addNonCanonicalSidecarVersionedHashes(dataColumnSidecar, updater);
    IntStream.range(0, dataColumnSidecar.getKzgCommitments().size())
        .forEach(
            index ->
                verify(commitmentToVersionedHashFunction)
                    .apply(dataColumnSidecar.getKzgCommitments().get(index).getKZGCommitment()));
    verify(updater, times(dataColumnSidecar.getKzgCommitments().size()))
        .addVersionedHash(eq(versionedHash.get()), any());
    verify(dao).getNonCanonicalDataColumnIdentifiers(eq(dataColumnSidecar.getSlotAndBlockRoot()));

    // stored only once, ignores next sidecars from the same block
    versionedHashDBSource.addSidecarVersionedHashes(
        dataStructureUtil.randomDataColumnSidecar(
            DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader(),
            dataColumnSidecar.getIndex().increment()),
        updater);
    verifyNoMoreInteractions(dao, updater, commitmentToVersionedHashFunction);
  }

  @Test
  public void removeSidecarVersionedHashes() {
    final KvStoreCombinedDao.FinalizedUpdater updater =
        mock(KvStoreCombinedDao.FinalizedUpdater.class);
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier =
        DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar);
    when(dao.getSidecar(dataColumnSlotAndIdentifier))
        .thenReturn(Optional.of(dataColumnSidecar.sszSerialize()));

    versionedHashDBSource.removeSidecarVersionedHashes(
        dataColumnSidecar.getKzgCommitments(), updater);
    // not activated yet
    verifyNoInteractions(updater, dao);

    versionedHashDBSource.storeSidecarHashes();
    versionedHashDBSource.removeSidecarVersionedHashes(
        dataColumnSidecar.getKzgCommitments(), updater);
    IntStream.range(0, dataColumnSidecar.getKzgCommitments().size())
        .forEach(
            index ->
                verify(commitmentToVersionedHashFunction)
                    .apply(dataColumnSidecar.getKzgCommitments().get(index).getKZGCommitment()));
    verify(updater, times(dataColumnSidecar.getKzgCommitments().size()))
        .removeVersionedHash(eq(versionedHash.get()));
    verifyNoMoreInteractions(dao, updater, commitmentToVersionedHashFunction);
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
    assertThat(sidecarIdentifier.dataColumnSidecarsIdentifierAndBlobIndex()).isEmpty();
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
    assertThat(sidecarIdentifier.dataColumnSidecarsIdentifierAndBlobIndex()).isEmpty();
    assertThat(sidecarIdentifier.blobSidecarIdentifier()).contains(expectedBlobIdentifier);
  }

  @Test
  public void computeCanonicalDataColumnSidecarMetadata_SerializeDeSerialize() {
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    final UInt64 expectedBlobIndex = UInt64.valueOf(123);
    final Bytes data =
        VersionedHashDBSource.computeCanonicalDataColumnSidecarMetadata(
            dataColumnSidecar.getSlotAndBlockRoot(), expectedBlobIndex);
    when(dao.getSidecarIdentifierData(any())).thenReturn(Optional.of(data));

    final Optional<SidecarIdentifier> maybeSidecarIdentifier =
        versionedHashDBSource.getSidecarIdentifier(versionedHash);
    verify(dao).getSidecarIdentifierData(any());
    final SidecarIdentifier expectedSidecarIdentifier =
        new SidecarIdentifier(
            Optional.empty(),
            Optional.of(Pair.of(dataColumnSidecar.getSlotAndBlockRoot(), expectedBlobIndex)),
            true);
    assertThat(maybeSidecarIdentifier).contains(expectedSidecarIdentifier);
  }

  @Test
  public void computeNonCanonicalDataColumnSidecarMetadata_SerializeDeSerialize() {
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    final UInt64 expectedBlobIndex = UInt64.valueOf(123);
    final Bytes data =
        VersionedHashDBSource.computeNonCanonicalDataColumnSidecarMetadata(
            dataColumnSidecar.getSlotAndBlockRoot(), expectedBlobIndex);
    when(dao.getSidecarIdentifierData(any())).thenReturn(Optional.of(data));

    final Optional<SidecarIdentifier> maybeSidecarIdentifier =
        versionedHashDBSource.getSidecarIdentifier(versionedHash);
    verify(dao).getSidecarIdentifierData(any());
    final SidecarIdentifier expectedSidecarIdentifier =
        new SidecarIdentifier(
            Optional.empty(),
            Optional.of(Pair.of(dataColumnSidecar.getSlotAndBlockRoot(), expectedBlobIndex)),
            false);
    assertThat(maybeSidecarIdentifier).contains(expectedSidecarIdentifier);
  }
}
