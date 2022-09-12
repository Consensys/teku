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

package tech.pegasys.teku.ethereum.json.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class EthereumTypesTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldDeserializeBLSSignatureFromString() {
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final String serialized = signature.toString();
    assertThat(EthereumTypes.SIGNATURE_TYPE.deserializeFromString(serialized)).isEqualTo(signature);
  }
}
