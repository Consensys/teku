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

package tech.pegasys.teku.api.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

public class Eth1DataTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final tech.pegasys.teku.datastructures.blocks.Eth1Data eth1DataInternal =
      dataStructureUtil.randomEth1Data();

  @Test
  public void shouldConvertToInternalObject() {
    final Eth1Data eth1Data = new Eth1Data(eth1DataInternal);
    assertThat(eth1Data.asInternalEth1Data()).isEqualTo(eth1DataInternal);
  }
}
