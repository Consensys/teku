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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.spec.propertytest.suppliers.type.Bytes48Supplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.DiverseBlobBytesSupplier;

@AddLifecycleHook(KzgResolver.class)
public class CKZG4844PropertyTest {

  @Property(tries = 100)
  void fuzzVerifyBlobKzgProof(
      final KZG kzg,
      @ForAll(supplier = DiverseBlobBytesSupplier.class) final Bytes blob,
      @ForAll(supplier = Bytes48Supplier.class) final Bytes48 commitment,
      @ForAll(supplier = Bytes48Supplier.class) final Bytes48 proof) {
    try {
      kzg.verifyBlobKzgProof(blob, commitment, proof);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzVerifyBlobKzgProofBatch(
      final KZG kzg,
      @ForAll final List<@From(supplier = DiverseBlobBytesSupplier.class) Bytes> blobs,
      @ForAll final List<@From(supplier = Bytes48Supplier.class) Bytes48> commitments,
      @ForAll final List<@From(supplier = Bytes48Supplier.class) Bytes48> proofs) {
    try {
      kzg.verifyBlobKzgProofBatch(blobs, commitments, proofs);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzBlobToKzgCommitment(
      final KZG kzg, @ForAll(supplier = DiverseBlobBytesSupplier.class) final Bytes blob) {
    try {
      kzg.blobToKzgCommitment(blob);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzBlobToKzgProof(
      final KZG kzg, @ForAll(supplier = DiverseBlobBytesSupplier.class) final Bytes blob) {
    try {
      final Bytes48 kzgCommitment = kzg.blobToKzgCommitment(blob);
      kzg.computeBlobKzgProof(blob, kzgCommitment);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }
}
