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
import java.util.Objects;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.propertytest.suppliers.SpecSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blobs.versions.deneb.BlobSidecarIndexSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blobs.versions.deneb.BlobSidecarSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blobs.versions.deneb.BlobSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.SignedBeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.deneb.BeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.KZGCommitmentSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.SszKZGCommitmentSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.SszKZGProofSupplier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class MiscHelpersDenebPropertyTest {

  private final Spec spec =
      Objects.requireNonNull(new SpecSupplier(SpecMilestone.DENEB).get()).sample();
  private final SpecConfigDeneb specConfig =
      spec.getGenesisSpecConfig().toVersionDeneb().orElseThrow();
  private final Predicates predicates = spec.getGenesisSpec().predicates();
  private final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
      spec.getGenesisSchemaDefinitions().toVersionDeneb().orElseThrow();
  private final MiscHelpersDeneb miscHelpers =
      new MiscHelpersDeneb(specConfig, predicates, schemaDefinitionsDeneb);

  @Property(tries = 100)
  void fuzzKzgCommitmentToVersionedHash(
      @ForAll(supplier = KZGCommitmentSupplier.class) final KZGCommitment commitment) {
    miscHelpers.kzgCommitmentToVersionedHash(commitment);
  }

  @AddLifecycleHook(KzgResolver.class)
  @Property(tries = 100)
  void fuzzVerifyBlobKzgProof(
      final KZG kzg, @ForAll(supplier = BlobSidecarSupplier.class) BlobSidecar blobSidecar) {
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
  void fuzzValidateBlobSidecarsBatchAgainstBlock(
      @ForAll final List<@From(supplier = BlobSidecarSupplier.class) BlobSidecar> blobSidecars,
      @ForAll(supplier = BeaconBlockSupplier.class) final BeaconBlock block,
      @ForAll
          final List<@From(supplier = KZGCommitmentSupplier.class) KZGCommitment> kzgCommitments) {
    try {
      miscHelpers.validateBlobSidecarsBatchAgainstBlock(blobSidecars, block, kzgCommitments);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Property(tries = 100)
  void fuzzVerifyBlobSidecarCompleteness(
      @ForAll final List<@From(supplier = BlobSidecarSupplier.class) BlobSidecar> blobSidecars,
      @ForAll
          final List<@From(supplier = KZGCommitmentSupplier.class) KZGCommitment> kzgCommitments) {
    try {
      miscHelpers.verifyBlobSidecarCompleteness(blobSidecars, kzgCommitments);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Property(tries = 100)
  void fuzzComputeBlobSidecar(
      @ForAll(supplier = SignedBeaconBlockSupplier.class) final SignedBeaconBlock signedBlock,
      @ForAll(supplier = BlobSidecarIndexSupplier.class) final UInt64 index,
      @ForAll(supplier = BlobSupplier.class) final Blob blob,
      @ForAll(supplier = SszKZGCommitmentSupplier.class) final SszKZGCommitment commitment,
      @ForAll(supplier = SszKZGProofSupplier.class) final SszKZGProof proof) {
    miscHelpers.computeBlobSidecar(signedBlock, index, blob, commitment, proof);
  }

  @Property(tries = 100)
  void fuzzVerifyBlobSidecarMerkleProof(
      @ForAll(supplier = BlobSidecarSupplier.class) final BlobSidecar blobSidecar) {
    miscHelpers.verifyBlobSidecarMerkleProof(blobSidecar);
  }
}
