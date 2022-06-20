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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ExecutionPayloadSerializerTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();

  @Test
  void shouldRoundTrip() {
    final Bytes data = payload.sszSerialize();

    final ExecutionPayload result = spec.deserializeExecutionPayload(data);

    assertThat(result).isEqualTo(payload);
  }

  @Test
  void shouldRoundtripWithPayloadSerializer() {
    final ExecutionPayloadSerializer serializer = new ExecutionPayloadSerializer(spec);
    final byte[] data = serializer.serialize(payload);
    final ExecutionPayload result = serializer.deserialize(data);
    assertThat(result).isEqualTo(payload);
  }

  @Test
  void shouldFailToDecodeIfBellatrixNotScheduled() {
    final Spec spec1 = TestSpecFactory.createMinimalAltair();
    assertThatThrownBy(() -> spec1.deserializeExecutionPayload(payload.sszSerialize()))
        .isInstanceOf(IllegalStateException.class);
  }
}
