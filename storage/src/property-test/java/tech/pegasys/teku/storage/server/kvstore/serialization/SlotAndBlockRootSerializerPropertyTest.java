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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.SLOT_AND_BLOCK_ROOT_SERIALIZER;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public class SlotAndBlockRootSerializerPropertyTest {
  @Property
  public void roundTrip(@ForAll final long slot, @ForAll @Size(32) final byte[] blockRoot) {
    final SlotAndBlockRoot value =
        new SlotAndBlockRoot(UInt64.fromLongBits(slot), Bytes32.wrap(blockRoot));
    final byte[] serialized = SLOT_AND_BLOCK_ROOT_SERIALIZER.serialize(value);
    final SlotAndBlockRoot deserialized = SLOT_AND_BLOCK_ROOT_SERIALIZER.deserialize(serialized);
    assertThat(deserialized).isEqualTo(value);
  }
}
