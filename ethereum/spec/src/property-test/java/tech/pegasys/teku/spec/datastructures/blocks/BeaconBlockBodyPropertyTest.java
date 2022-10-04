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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;

public class BeaconBlockBodyPropertyTest {
  @Property
  @SuppressWarnings("unchecked")
  void roundTrip(@ForAll(supplier = BeaconBlockBodySupplier.class) final BeaconBlockBody body)
      throws JsonProcessingException {
    final BeaconBlockBodySchema<?> schema = body.getSchema();
    final DeserializableTypeDefinition<BeaconBlockBody> typeDefinition =
        (DeserializableTypeDefinition<BeaconBlockBody>) schema.getJsonTypeDefinition();

    // Round-trip SSZ serialization.
    final Bytes ssz = body.sszSerialize();
    final BeaconBlockBody fromSsz = schema.sszDeserialize(ssz);
    assertThat(fromSsz).isEqualTo(body);

    // Round-trip JSON serialization.
    final String json = JsonUtil.serialize(body, typeDefinition);
    final BeaconBlockBody fromJson = JsonUtil.parse(json, typeDefinition);
    assertThat(fromJson).isEqualTo(body);
  }
}
