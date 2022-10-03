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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;

public class SyncAggregatePropertyTest {
  @Property
  void roundTrip(@ForAll(supplier = SyncAggregateSupplier.class) final SyncAggregate syncAggregate)
      throws JsonProcessingException {
    final SyncAggregateSchema schema = syncAggregate.getSchema();
    final DeserializableTypeDefinition<SyncAggregate> typeDefinition =
        schema.getJsonTypeDefinition();

    // Round-trip SSZ serialization.
    final Bytes ssz = syncAggregate.sszSerialize();
    final SyncAggregate fromSsz = schema.sszDeserialize(ssz);
    assertThat(fromSsz).isEqualTo(syncAggregate);

    // Round-trip JSON serialization.
    final String json = JsonUtil.serialize(syncAggregate, typeDefinition);
    final SyncAggregate fromJson = JsonUtil.parse(json, typeDefinition);
    assertThat(fromJson).isEqualTo(syncAggregate);
  }
}
