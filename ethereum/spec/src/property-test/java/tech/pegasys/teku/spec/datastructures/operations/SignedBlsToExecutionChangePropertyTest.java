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

package tech.pegasys.teku.spec.datastructures.operations;

import static tech.pegasys.teku.spec.datastructures.util.PropertyTestHelper.assertDeserializeMutatedThrowsExpected;
import static tech.pegasys.teku.spec.datastructures.util.PropertyTestHelper.assertRoundTrip;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SignedBlsToExecutionChangePropertyTest {

  @Property
  void roundTrip(
      @ForAll(supplier = SignedBlsToExecutionChangeSupplier.class)
          final SignedBlsToExecutionChange signedBlsToExecutionChange)
      throws JsonProcessingException {
    assertRoundTrip(signedBlsToExecutionChange);
  }

  @Property
  void deserializeMutated(
      @ForAll(supplier = SignedBlsToExecutionChangeSupplier.class)
          final SignedBlsToExecutionChange signedBlsToExecutionChange,
      @ForAll final int seed) {
    assertDeserializeMutatedThrowsExpected(signedBlsToExecutionChange, seed);
  }

  public static class SignedBlsToExecutionChangeSupplier
      extends DataStructureUtilSupplier<SignedBlsToExecutionChange> {

    public SignedBlsToExecutionChangeSupplier() {
      super(DataStructureUtil::randomSignedBlsToExecutionChange, SpecMilestone.CAPELLA);
    }
  }
}
