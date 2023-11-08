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
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarOld;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.propertytest.suppliers.SpecSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blobs.versions.deneb.BlobSidecarOldSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.deneb.BeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.KZGCommitmentSupplier;

public class MiscHelpersDenebPropertyTest {

  private final SpecConfigDeneb specConfig =
      Objects.requireNonNull(new SpecSupplier(SpecMilestone.DENEB).get())
          .sample()
          .getGenesisSpecConfig()
          .toVersionDeneb()
          .orElseThrow();
  private final MiscHelpersDeneb miscHelpers = new MiscHelpersDeneb(specConfig);

  @Property(tries = 100)
  void fuzzKzgCommitmentToVersionedHash(
      @ForAll(supplier = KZGCommitmentSupplier.class) final KZGCommitment commitment) {
    miscHelpers.kzgCommitmentToVersionedHash(commitment);
  }

  @AddLifecycleHook(KzgResolver.class)
  @Property(tries = 100)
  void fuzzVerifyBlobKzgProof(
      final KZG kzg, @ForAll(supplier = BlobSidecarOldSupplier.class) BlobSidecarOld blobSidecar) {
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
      @ForAll
          final List<@From(supplier = BlobSidecarOldSupplier.class) BlobSidecarOld> blobSidecars) {
    try {
      miscHelpers.verifyBlobKzgProofBatch(kzg, blobSidecars);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzValidateBlobSidecarsBatchAgainstBlock(
      @ForAll
          final List<@From(supplier = BlobSidecarOldSupplier.class) BlobSidecarOld> blobSidecars,
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
      @ForAll
          final List<@From(supplier = BlobSidecarOldSupplier.class) BlobSidecarOld> blobSidecars,
      @ForAll
          final List<@From(supplier = KZGCommitmentSupplier.class) KZGCommitment> kzgCommitments) {
    try {
      miscHelpers.verifyBlobSidecarCompleteness(blobSidecars, kzgCommitments);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }
}
