/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.statetransition.protoarray;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ProtoArrayTest {

  @Test
  void listSplitTest() {
    List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));

    list.subList(0, 2).clear();

    assertThat(list.get(0)).isEqualTo(3);
    assertThat(list.size()).isEqualTo(4);
  }
}
