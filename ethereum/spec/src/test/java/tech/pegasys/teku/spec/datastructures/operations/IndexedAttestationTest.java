/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true)
class IndexedAttestationTest {
  private DataStructureUtil dataStructureUtil;

  private IndexedAttestationContainer indexedAttestation;
  private IndexedAttestationContainer newIndexedAttestation;

  @BeforeEach
  public void setup(TestSpecInvocationContextProvider.SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
    indexedAttestation = dataStructureUtil.randomIndexedAttestation();
    newIndexedAttestation =
        (IndexedAttestationContainer)
            indexedAttestation.getSchema().sszDeserialize(indexedAttestation.sszSerialize());
  }

  @TestTemplate
  void roundTripViaSsz() {
    assertEquals(indexedAttestation, newIndexedAttestation);
  }
}
