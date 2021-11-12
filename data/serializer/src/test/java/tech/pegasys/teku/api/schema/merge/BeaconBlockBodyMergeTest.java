/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.api.schema.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.SchemaObjectProvider;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconBlockBodyMergeTest {
  private final Spec spec = TestSpecFactory.createMinimalMerge();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaObjectProvider schemaObjectProvider = new SchemaObjectProvider(spec);

  @Test
  void asInternalBeaconBlockBody_ShouldConvertMergeBlock() {
    final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock internalBlock =
        dataStructureUtil.randomBeaconBlock(ONE);
    final BeaconBlock block = schemaObjectProvider.getBeaconBlock(internalBlock);

    assertThat(block).isInstanceOf(BeaconBlockMerge.class);
    assertThat(block.asInternalBeaconBlock(spec)).isEqualTo(internalBlock);
  }
}
