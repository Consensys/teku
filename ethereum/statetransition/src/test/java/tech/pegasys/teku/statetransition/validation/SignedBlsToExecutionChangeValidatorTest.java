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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class SignedBlsToExecutionChangeValidatorTest {

  private final Spec spec = TestSpecFactory.createMinimalCapella();

  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();

  private RecentChainData recentChainData;

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private SignedBlsToExecutionChangeValidator validator;

  @BeforeEach
  public void beforeEach() {
    storageSystem.chainUpdater().initializeGenesis();
    recentChainData = storageSystem.recentChainData();

    validator = new SignedBlsToExecutionChangeValidator(spec, recentChainData);
  }

  @Test
  public void shouldAcceptValidMessage() {
    final SignedBlsToExecutionChange message = dataStructureUtil.randomSignedBlsToExecutionChange();

    final SafeFuture<InternalValidationResult> validationResult = validator.validateFully(message);

    assertValidationResult(validationResult, ValidationResultCode.ACCEPT);
  }

  @Test
  public void shouldIgnoreSubsequentMessagesForSameValidator() {
    final SignedBlsToExecutionChange message = dataStructureUtil.randomSignedBlsToExecutionChange();

    final SafeFuture<InternalValidationResult> firstValidationResult =
        validator.validateFully(message);
    final SafeFuture<InternalValidationResult> secondValidationResult =
        validator.validateFully(message);

    assertValidationResult(firstValidationResult, ValidationResultCode.ACCEPT);
    assertValidationResult(secondValidationResult, ValidationResultCode.IGNORE);
  }

  @Test
  public void shouldRejectMessageFailingValidation() {
    fail();
  }

  private void assertValidationResult(
      final SafeFuture<InternalValidationResult> validationResult,
      final ValidationResultCode expectedResultCode) {
    assertThat(validationResult)
        .isCompletedWithValueMatching(result -> result.code() == expectedResultCode);
  }
}
