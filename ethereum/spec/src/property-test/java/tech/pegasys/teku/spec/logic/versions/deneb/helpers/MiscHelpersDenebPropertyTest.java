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

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.propertytest.suppliers.blobs.versions.deneb.BlobSidecarIndexSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blobs.versions.deneb.BlobSidecarSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blobs.versions.deneb.BlobSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.deneb.SignedBeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.KZGCommitmentSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.SszKZGProofSupplier;

public class MiscHelpersDenebPropertyTest {

  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final MiscHelpersDeneb miscHelpers =
      MiscHelpersDeneb.required(spec.getGenesisSpec().miscHelpers());

  @Property(tries = 100)
  void fuzzKzgCommitmentToVersionedHash(
      @ForAll(supplier = KZGCommitmentSupplier.class) final KZGCommitment commitment) {
    miscHelpers.kzgCommitmentToVersionedHash(commitment);
  }

  @AddLifecycleHook(KzgResolver.class)
  @Property(tries = 100)
  void fuzzVerifyBlobKzgProof(
      final KZG kzg, @ForAll(supplier = BlobSidecarSupplier.class) final BlobSidecar blobSidecar) {
    try {
      miscHelpers.verifyBlobKzgProof(kzg, blobSidecar);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @AddLifecycleHook(KzgResolver.class)
  @Property(tries = 100)
  void fuzzVerifyBlobKzgProofBatch(
      final KZG kzg,
      @ForAll final List<@From(supplier = BlobSidecarSupplier.class) BlobSidecar> blobSidecars) {
    try {
      miscHelpers.verifyBlobKzgProofBatch(kzg, blobSidecars);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzVerifyBlobSidecarCompleteness(
      @ForAll final List<@From(supplier = BlobSidecarSupplier.class) BlobSidecar> blobSidecars,
      @ForAll(supplier = SignedBeaconBlockSupplier.class)
          final SignedBeaconBlock signedBeaconBlock) {
    try {
      miscHelpers.verifyBlobSidecarCompleteness(blobSidecars, signedBeaconBlock);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Property(tries = 100)
  void fuzzVerifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(
      @ForAll final List<@From(supplier = BlobSidecarSupplier.class) BlobSidecar> blobSidecars,
      @ForAll(supplier = SignedBeaconBlockSupplier.class)
          final SignedBeaconBlock signedBeaconBlock) {

    miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(
        blobSidecars, signedBeaconBlock);
  }

  @Property(tries = 100)
  void fuzzConstructBlobSidecarAndVerifyMerkleProof(
      @ForAll(supplier = SignedBeaconBlockSupplier.class) final SignedBeaconBlock signedBeaconBlock,
      @ForAll(supplier = BlobSidecarIndexSupplier.class) final UInt64 index,
      @ForAll(supplier = BlobSupplier.class) final Blob blob,
      @ForAll(supplier = SszKZGProofSupplier.class) final SszKZGProof proof) {
    try {
      final BlobSidecar blobSidecar =
          miscHelpers.constructBlobSidecar(signedBeaconBlock, index, blob, proof);
      miscHelpers.verifyBlobKzgCommitmentInclusionProof(blobSidecar);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }
}
