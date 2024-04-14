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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerWithdrawRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerWithdrawRequestSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionLayerWithdrawRequestTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimal(SpecMilestone.ELECTRA));
  private final ExecutionLayerWithdrawRequestSchema executionLayerWithdrawRequestSchema =
      new ExecutionLayerWithdrawRequestSchema();
  private final Bytes20 sourceAddress = dataStructureUtil.randomBytes20();
  private final BLSPublicKey validatorPublicKey = dataStructureUtil.randomPublicKey();
  private final UInt64 amount = dataStructureUtil.randomUInt64();

  @Test
  public void objectEquality() {
    final ExecutionLayerWithdrawRequest executionLayerWithdrawRequest1 =
        executionLayerWithdrawRequestSchema.create(sourceAddress, validatorPublicKey, amount);
    final ExecutionLayerWithdrawRequest executionLayerWithdrawRequest2 =
        executionLayerWithdrawRequestSchema.create(sourceAddress, validatorPublicKey, amount);

    assertThat(executionLayerWithdrawRequest1).isEqualTo(executionLayerWithdrawRequest2);
  }

  @Test
  public void objectAccessorMethods() {
    final ExecutionLayerWithdrawRequest executionLayerWithdrawRequest =
        executionLayerWithdrawRequestSchema.create(sourceAddress, validatorPublicKey, amount);

    assertThat(executionLayerWithdrawRequest.getSourceAddress()).isEqualTo(sourceAddress);
    assertThat(executionLayerWithdrawRequest.getValidatorPublicKey()).isEqualTo(validatorPublicKey);
  }

  @Test
  public void roundTripSSZ() {
    final ExecutionLayerWithdrawRequest executionLayerWithdrawRequest =
        executionLayerWithdrawRequestSchema.create(sourceAddress, validatorPublicKey, amount);

    final Bytes sszBytes = executionLayerWithdrawRequest.sszSerialize();
    final ExecutionLayerWithdrawRequest deserializedObject =
        executionLayerWithdrawRequestSchema.sszDeserialize(sszBytes);

    assertThat(executionLayerWithdrawRequest).isEqualTo(deserializedObject);
  }
}
