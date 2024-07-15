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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.createStateSerializer;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.propertytest.suppliers.SpecSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconStateSerializerPropertyTest {
  @Property(tries = 20)
  public boolean roundTrip(
      @ForAll final int seed,
      @ForAll(supplier = SpecSupplier.class) final Spec spec,
      @ForAll @IntRange(max = 1000) final int validatorCount) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final KvStoreSerializer<BeaconState> serializer = createStateSerializer(spec);
    final BeaconState value = dataStructureUtil.randomBeaconState(validatorCount);
    final byte[] serialized = serializer.serialize(value);
    final BeaconState deserialized = serializer.deserialize(serialized);
    return deserialized.equals(value);
  }
}
