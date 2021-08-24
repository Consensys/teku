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

package tech.pegasys.teku.validator.client.duties.attestations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;

class IndividualSendingStrategyTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @SuppressWarnings("unchecked")
  private final Function<ProductionResult<String>, SafeFuture<DutyResult>> sendFunction =
      mock(Function.class);

  // TODO: Should make this use a mock send function
  private final IndividualSendingStrategy<String> strategy =
      new IndividualSendingStrategy<>(sendFunction);

  @BeforeEach
  void setUp() {
    when(sendFunction.apply(any()))
        .thenAnswer(
            invocation -> {
              final ProductionResult<String> result = invocation.getArgument(0);
              return SafeFuture.completedFuture(result.getResult());
            });
  }

  @Test
  void shouldSendAttestationsAsSoonAsTheyAreReady() {
    final SafeFuture<ProductionResult<String>> future1 = new SafeFuture<>();
    final SafeFuture<ProductionResult<String>> future2 = new SafeFuture<>();
    final SafeFuture<ProductionResult<String>> future3 = new SafeFuture<>();

    final String message1 = "message1";
    final String message2 = "message2";
    final String message3 = "message3";

    final SafeFuture<DutyResult> result = strategy.send(Stream.of(future1, future2, future3));

    assertThat(result).isNotDone();

    final ProductionResult<String> result1 =
        ProductionResult.success(
            dataStructureUtil.randomPublicKey(), dataStructureUtil.randomBytes32(), message1);
    future1.complete(result1);
    assertThat(result).isNotDone();
    verify(sendFunction).apply(result1);

    final ProductionResult<String> result3 =
        ProductionResult.success(
            dataStructureUtil.randomPublicKey(), dataStructureUtil.randomBytes32(), message3);
    future3.complete(result3);
    assertThat(result).isNotDone();
    verify(sendFunction).apply(result3);

    final ProductionResult<String> result2 =
        ProductionResult.success(
            dataStructureUtil.randomPublicKey(), dataStructureUtil.randomBytes32(), message2);
    future2.complete(result2);
    assertThat(result).isCompleted();
    assertThat(result.join().getSuccessCount()).isEqualTo(3);
    verify(sendFunction).apply(result2);
  }
}
