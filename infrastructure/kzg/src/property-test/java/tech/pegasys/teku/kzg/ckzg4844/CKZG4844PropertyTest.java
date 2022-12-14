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

package tech.pegasys.teku.kzg.ckzg4844;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import ethereum.ckzg4844.CKZG4844JNI;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.kzg.propertytest.suppliers.Bytes32Supplier;
import tech.pegasys.teku.kzg.propertytest.suppliers.BytesSupplier;
import tech.pegasys.teku.kzg.propertytest.suppliers.KZGCommitmentSupplier;
import tech.pegasys.teku.kzg.propertytest.suppliers.KZGProofSupplier;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetups;

public class CKZG4844PropertyTest {
  private static KZG kzg = CKZG4844.createInstance(CKZG4844JNI.Preset.MAINNET.fieldElementsPerBlob);

  static {
    final String trustedSetup =
        Resources.getResource(TrustedSetups.class, "mainnet/trusted_setup.txt").toExternalForm();
    kzg.loadTrustedSetup(trustedSetup);
  }

  @Property(tries = 100)
  void computeAggregateKzgProofThrowsExpected(
      @ForAll final List<@From(supplier = BytesSupplier.class) Bytes> blobs) {
    try {
      kzg.computeAggregateKzgProof(blobs);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void verifyAggregateKzgProofThrowsExpected(
      @ForAll final List<@From(supplier = BytesSupplier.class) Bytes> blobs,
      @ForAll final List<@From(supplier = KZGCommitmentSupplier.class) KZGCommitment> commitments,
      @ForAll(supplier = KZGProofSupplier.class) final KZGProof proof) {
    try {
      kzg.verifyAggregateKzgProof(blobs, commitments, proof);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void blobToKzgCommitmentThrowsExpected(@ForAll(supplier = BytesSupplier.class) final Bytes blob) {
    try {
      kzg.blobToKzgCommitment(blob);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }
}
