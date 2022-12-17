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

package tech.pegasys.teku.spec.logic.versions.eip4844.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.propertytest.suppliers.type.BytesSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.KZGCommitmentSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.KZGProofSupplier;

@AddLifecycleHook(KzgResolver.class)
public class CKZG4844PropertyTest {
  @Property(tries = 100)
  void fuzzComputeAggregateKzgProof(
      final KZG kzg, @ForAll final List<@From(supplier = BytesSupplier.class) Bytes> blobs) {
    try {
      kzg.computeAggregateKzgProof(blobs);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzVerifyAggregateKzgProof(
      final KZG kzg,
      @ForAll final List<@From(supplier = BytesSupplier.class) Bytes> blobs,
      @ForAll final List<@From(supplier = KZGCommitmentSupplier.class) KZGCommitment> commitments,
      @ForAll(supplier = KZGProofSupplier.class) final KZGProof proof) {
    try {
      kzg.verifyAggregateKzgProof(blobs, commitments, proof);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 1000)
  void fuzzBlobToKzgCommitment(
      final KZG kzg, @ForAll(supplier = BytesSupplier.class) final Bytes blob) {
    try {
      System.out.println(blob.toHexString());
      kzg.blobToKzgCommitment(blob);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }
}
