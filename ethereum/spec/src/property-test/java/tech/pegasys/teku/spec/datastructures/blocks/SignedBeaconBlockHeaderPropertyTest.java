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

package tech.pegasys.teku.spec.datastructures.blocks;

import static tech.pegasys.teku.spec.propertytest.util.PropertyTestHelper.assertDeserializeMutatedThrowsExpected;
import static tech.pegasys.teku.spec.propertytest.util.PropertyTestHelper.assertRoundTrip;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.SignedBeaconBlockHeaderSupplier;

public class SignedBeaconBlockHeaderPropertyTest {
  @Property
  void roundTrip(
      @ForAll(supplier = SignedBeaconBlockHeaderSupplier.class)
          final SignedBeaconBlockHeader signedBeaconBlockHeader)
      throws JsonProcessingException {
    assertRoundTrip(signedBeaconBlockHeader);
  }

  @Property
  void deserializeMutated(
      @ForAll(supplier = SignedBeaconBlockHeaderSupplier.class)
          final SignedBeaconBlockHeader signedBeaconBlockHeader,
      @ForAll final int seed) {
    assertDeserializeMutatedThrowsExpected(signedBeaconBlockHeader, seed);
  }
}
