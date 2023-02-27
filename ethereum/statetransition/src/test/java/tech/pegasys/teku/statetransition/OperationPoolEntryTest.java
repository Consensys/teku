/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.statetransition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;

public class OperationPoolEntryTest {
  private final SszBytes4 b0 = SszBytes4.of(Bytes4.fromHexString("0xFFFFFF00"));
  private final SszBytes4 b1 = SszBytes4.of(Bytes4.fromHexString("0xFFFFFF11"));
  private final SszBytes4 b2 = SszBytes4.of(Bytes4.fromHexString("0xFFFFFF22"));

  @Test
  void shouldSortLocalFirst() {
    final List<OperationPoolEntry<SszBytes4>> list = new ArrayList<>();

    list.add(new OperationPoolEntry<>(b2, true));
    list.add(new OperationPoolEntry<>(b0, false));
    list.add(new OperationPoolEntry<>(b1, true));

    assertThat(
            list.stream().sorted().map(OperationPoolEntry::getMessage).collect(Collectors.toList()))
        .containsExactly(b2, b1, b0);
  }
}
