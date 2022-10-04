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

package tech.pegasys.teku.spec.datastructures.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;

public class BeaconStatePropertyTest {
  @Property(tries = 100)
  @SuppressWarnings("unchecked")
  void roundTrip(@ForAll(supplier = BeaconStateSupplier.class) final BeaconState state)
      throws JsonProcessingException {
    final BeaconStateSchema<?, ?> schema = state.getBeaconStateSchema();
    final DeserializableTypeDefinition<BeaconState> typeDefinition =
        (DeserializableTypeDefinition<BeaconState>) schema.getJsonTypeDefinition();

    // Round-trip SSZ serialization.
    final Bytes ssz = state.sszSerialize();
    final BeaconState fromSsz = schema.sszDeserialize(ssz);
    assertThat(fromSsz).isEqualTo(state);

    // Round-trip JSON serialization.
    final String json = JsonUtil.serialize(state, typeDefinition);
    final BeaconState fromJson = JsonUtil.parse(json, typeDefinition);
    assertThat(fromJson).isEqualTo(state);
  }
}
