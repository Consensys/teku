/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.deneb.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.spec.config.SpecConfigDeneb.VERSIONED_HASH_VERSION_KZG;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MiscHelpersDenebTest {

  private static final VersionedHash VERSIONED_HASH =
      VersionedHash.create(
          VERSIONED_HASH_VERSION_KZG,
          Bytes32.fromHexString(
              "0x391610cf24e7c540192b80ddcfea77b0d3912d94e922682f3b286eee041e6f76"));

  private static final KZGCommitment KZG_COMMITMENT =
      KZGCommitment.fromHexString(
          "0xb09ce4964278eff81a976fbc552488cb84fc4a102f004c87179cb912f49904d1e785ecaf5d184522a58e9035875440ef");

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final MiscHelpersDeneb miscHelpersDeneb =
      new MiscHelpersDeneb(spec.getGenesisSpecConfig().toVersionDeneb().orElseThrow());

  @Test
  public void isDataAvailable_shouldThrowIfCommitmentsDontMatch() {
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .slot(UInt64.ONE)
            .blockRoot(Bytes32.ZERO)
            .build();

    assertThatThrownBy(
            () ->
                miscHelpersDeneb.isDataAvailable(
                    UInt64.valueOf(1), Bytes32.ZERO, List.of(KZG_COMMITMENT), List.of(blobSidecar)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blob sidecar KZG commitment")
        .hasMessageContaining("does not match block KZG commitment");
  }

  @Test
  public void isDataAvailable_shouldThrowIfSlotDoesntMatch() {
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .slot(UInt64.valueOf(2))
            .blockRoot(Bytes32.ZERO)
            .build();

    assertThatThrownBy(
            () ->
                miscHelpersDeneb.isDataAvailable(
                    UInt64.ONE, Bytes32.ZERO, List.of(KZG_COMMITMENT), List.of(blobSidecar)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blob sidecar slot")
        .hasMessageContaining("does not match block slot");
  }

  @Test
  public void isDataAvailable_shouldThrowIfBlobSidecarsAndBlockCommitmentsHaveDifferentSizes() {
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .slot(UInt64.ONE)
            .blockRoot(Bytes32.ZERO)
            .build();

    assertThatThrownBy(
            () ->
                miscHelpersDeneb.isDataAvailable(
                    UInt64.ONE,
                    Bytes32.ZERO,
                    List.of(KZG_COMMITMENT, KZG_COMMITMENT),
                    List.of(blobSidecar)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blob sidecars count")
        .hasMessageContaining("does not match KZG commitments count");
  }

  @Test
  public void isDataAvailable_shouldThrowIfBlockRootDoesntMatch() {
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .slot(UInt64.ONE)
            .blockRoot(Bytes32.fromHexString("0x01"))
            .build();

    assertThatThrownBy(
            () ->
                miscHelpersDeneb.isDataAvailable(
                    UInt64.ONE, Bytes32.ZERO, List.of(KZG_COMMITMENT), List.of(blobSidecar)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blob sidecar block root")
        .hasMessageContaining("does not match block root");
  }

  @Test
  public void versionedHash() {
    final VersionedHash actual =
        miscHelpersDeneb.kzgCommitmentToVersionedHash(
            KZGCommitment.fromHexString(
                "0x85d1edf1ee88f68260e750abb2c766398ad1125d4e94e1de04034075ccbd2bb79c5689b952ef15374fd03ca2b2475371"));
    assertThat(actual).isEqualTo(VERSIONED_HASH);
  }
}
