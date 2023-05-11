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

package tech.pegasys.teku.spec.logic.versions.deneb.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfigDeneb.BLOB_TX_TYPE;
import static tech.pegasys.teku.spec.config.SpecConfigDeneb.VERSIONED_HASH_VERSION_KZG;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

class MiscHelpersDenebTest {

  private static final String SIGNED_BLOB_TX_LOCATION = "signed_blob_transaction.ssz";
  private static final VersionedHash VERSIONED_HASH =
      VersionedHash.create(
          VERSIONED_HASH_VERSION_KZG,
          Bytes32.fromHexString(
              "0x391610cf24e7c540192b80ddcfea77b0d3912d94e922682f3b286eee041e6f76"));

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final MiscHelpersDeneb miscHelpersDeneb =
      new MiscHelpersDeneb(spec.getGenesisSpecConfig().toVersionDeneb().orElseThrow());

  @Test
  public void versionedHash() {
    final VersionedHash actual =
        miscHelpersDeneb.kzgCommitmentToVersionedHash(
            KZGCommitment.fromHexString(
                "0x85d1edf1ee88f68260e750abb2c766398ad1125d4e94e1de04034075ccbd2bb79c5689b952ef15374fd03ca2b2475371"));
    assertThat(actual).isEqualTo(VERSIONED_HASH);
  }

  @Test
  public void txPeekBlobVersionedHashes() throws IOException {
    final Bytes sszTx = loadData(SIGNED_BLOB_TX_LOCATION);
    assertThat(sszTx.get(0)).isEqualTo(BLOB_TX_TYPE.get(0));
    final TransactionSchema transactionSchema =
        new TransactionSchema(spec.getGenesisSpecConfig().toVersionDeneb().orElseThrow());
    final Transaction blobTx = transactionSchema.sszDeserialize(sszTx);
    List<VersionedHash> versionedHashes = miscHelpersDeneb.txPeekBlobVersionedHashes(blobTx);
    assertThat(versionedHashes.size()).isEqualTo(5);
    assertThat(
            versionedHashes.stream().allMatch(hash -> hash.isVersion(VERSIONED_HASH_VERSION_KZG)))
        .isTrue();
    assertThat(versionedHashes.stream().allMatch(hash -> hash.equals(VERSIONED_HASH))).isTrue();
  }

  @Test
  public void shouldComputeDenebStartSlot() {
    assertThat(miscHelpersDeneb.computeDenebStartSlot()).isEqualTo(UInt64.valueOf(0));
    final Spec spec2 =
        TestSpecFactory.createDeneb(
            SpecConfigLoader.loadConfig(
                "minimal",
                phase0Builder ->
                    phase0Builder
                        .altairBuilder(altairBuilder -> altairBuilder.altairForkEpoch(UInt64.ZERO))
                        .bellatrixBuilder(
                            bellatrixBuilder -> bellatrixBuilder.bellatrixForkEpoch(UInt64.ZERO))
                        .capellaBuilder(
                            capellaBuilder -> capellaBuilder.capellaForkEpoch(UInt64.ZERO))
                        .denebBuilder(
                            denebBuilder ->
                                denebBuilder
                                    .denebForkEpoch(UInt64.valueOf(2))
                                    .kzgNoop(true)
                                    .trustedSetupPath(""))));
    final MiscHelpersDeneb miscHelpersDeneb2 =
        new MiscHelpersDeneb(spec2.getGenesisSpecConfig().toVersionDeneb().orElseThrow());
    assertThat(miscHelpersDeneb2.computeDenebStartSlot())
        .isEqualTo(UInt64.valueOf(spec2.slotsPerEpoch(UInt64.ZERO)).times(2));
  }

  private Bytes loadData(final String location) throws IOException {
    final ByteSource binaryData =
        Resources.asByteSource(Resources.getResource(MiscHelpersDenebTest.class, location));
    return Bytes.wrap(binaryData.read());
  }
}
