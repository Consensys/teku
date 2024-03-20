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
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerExit;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerExitSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionLayerExitTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimal(SpecMilestone.ELECTRA));
  private final ExecutionLayerExitSchema executionLayerExitSchema = new ExecutionLayerExitSchema();
  private final Bytes20 sourceAddress = dataStructureUtil.randomBytes20();
  private final BLSPublicKey validatorPublicKey = dataStructureUtil.randomPublicKey();

  @Test
  public void objectEquality() {
    final ExecutionLayerExit executionLayerExit1 =
        executionLayerExitSchema.create(sourceAddress, validatorPublicKey);
    final ExecutionLayerExit executionLayerExit2 =
        executionLayerExitSchema.create(sourceAddress, validatorPublicKey);

    assertThat(executionLayerExit1).isEqualTo(executionLayerExit2);
  }

  @Test
  public void objectAccessorMethods() {
    final ExecutionLayerExit executionLayerExit =
        executionLayerExitSchema.create(sourceAddress, validatorPublicKey);

    assertThat(executionLayerExit.getSourceAddress()).isEqualTo(sourceAddress);
    assertThat(executionLayerExit.getValidatorPublicKey()).isEqualTo(validatorPublicKey);
  }

  @Test
  public void roundTripSSZ() {
    final ExecutionLayerExit executionLayerExit =
        executionLayerExitSchema.create(sourceAddress, validatorPublicKey);

    final Bytes sszBytes = executionLayerExit.sszSerialize();
    final ExecutionLayerExit deserializedObject = executionLayerExitSchema.sszDeserialize(sszBytes);

    assertThat(executionLayerExit).isEqualTo(deserializedObject);
  }
}
