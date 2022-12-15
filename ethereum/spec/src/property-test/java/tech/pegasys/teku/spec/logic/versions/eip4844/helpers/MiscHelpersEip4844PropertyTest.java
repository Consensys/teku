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
import java.util.Objects;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.propertytest.suppliers.SpecSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.execution.TransactionSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.execution.versions.eip4844.BlobsSidecarSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.Bytes32Supplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.KZGCommitmentSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.UInt64Supplier;

public class MiscHelpersEip4844PropertyTest {
  private final SpecConfigEip4844 specConfig =
      Objects.requireNonNull(new SpecSupplier().get())
          .sample()
          .getGenesisSpecConfig()
          .toVersionEip4844()
          .orElseThrow();
  private final MiscHelpersEip4844 miscHelpers = new MiscHelpersEip4844(specConfig);

  @Property(tries = 100)
  void isDataAvailableThrowsExpected(
      @ForAll(supplier = UInt64Supplier.class) final UInt64 slot,
      @ForAll(supplier = Bytes32Supplier.class) final Bytes32 beaconBlockRoot,
      @ForAll final List<@From(supplier = KZGCommitmentSupplier.class) KZGCommitment> commitments,
      @ForAll(supplier = BlobsSidecarSupplier.class) final BlobsSidecar blobsSidecar) {
    try {
      miscHelpers.isDataAvailable(slot, beaconBlockRoot, commitments, blobsSidecar);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Property
  void kzgCommitmentToVersionedHashThrowsExpected(
      @ForAll(supplier = KZGCommitmentSupplier.class) final KZGCommitment commitment) {
    miscHelpers.kzgCommitmentToVersionedHash(commitment);
  }

  @Property(tries = 100)
  void txPeekBlobVersionedHashesThrowsExpected(
      @ForAll(supplier = TransactionSupplier.class) final Transaction transaction) {
    try {
      miscHelpers.txPeekBlobVersionedHashes(transaction);
    } catch (Exception e) {
      assertThat(e)
          .isInstanceOfAny(
              ArithmeticException.class,
              IllegalArgumentException.class,
              IndexOutOfBoundsException.class);
    }
  }

  @Property(tries = 100)
  void verifyKZGCommitmentsAgainstTransactionsThrowsExpected(
      @ForAll final List<@From(supplier = TransactionSupplier.class) Transaction> transactions,
      @ForAll final List<@From(supplier = KZGCommitmentSupplier.class) KZGCommitment> commitments) {
    try {
      miscHelpers.verifyKZGCommitmentsAgainstTransactions(transactions, commitments);
    } catch (Exception e) {
      assertThat(e)
          .isInstanceOfAny(
              ArithmeticException.class,
              IllegalArgumentException.class,
              IndexOutOfBoundsException.class);
    }
  }
}
