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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;

@TestSpecContext(allMilestones = true)
class SignedBeaconBlockTest {

  @TestTemplate
  public void shouldConvertSchemaToInternalCorrectly(SpecContext ctx) {

    final tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock =
        ctx.getDataStructureUtil().randomSignedBeaconBlock(1);
    final SignedBeaconBlock apiBlock = SignedBeaconBlock.create(internalBlock);
    assertThatSszData(apiBlock.asInternalSignedBeaconBlock(ctx.getSpec()))
        .isEqualByAllMeansTo(internalBlock);
  }
}
