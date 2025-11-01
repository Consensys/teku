/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.client.duties.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.validator.client.duties.execution.ExecutionPayloadDuty.EXECUTION_PAYLOAD_DUTY_DELAY_FOR_SELF_BUILT_BID;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.Validator;

class ExecutionPayloadDutyTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final Signer signer = mock(Signer.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);

  private final Validator validator =
      new Validator(
          dataStructureUtil.randomPublicKey(),
          signer,
          new FileBackedGraffitiProvider(
              Optional.of(dataStructureUtil.randomBytes32()), Optional.empty()));
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);

  private ExecutionPayloadDuty duty;

  @BeforeEach
  public void setUp() {
    duty = new ExecutionPayloadDuty(spec, asyncRunner, validatorApiChannel, validatorLogger);
  }

  @Test
  public void performsDuty_onSelfBuiltBidIncludedInBlock() {
    final ExecutionPayloadBid bid = dataStructureUtil.randomExecutionPayloadBid();

    final SignedExecutionPayloadEnvelope signedExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(42);

    when(validatorApiChannel.createUnsignedExecutionPayload(bid.getSlot(), bid.getBuilderIndex()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(signedExecutionPayload.getMessage())));
    when(signer.signExecutionPayloadEnvelope(signedExecutionPayload.getMessage(), fork))
        .thenReturn(SafeFuture.completedFuture(signedExecutionPayload.getSignature()));
    when(validatorApiChannel.publishSignedExecutionPayload(any())).thenReturn(SafeFuture.COMPLETE);

    duty.onSelfBuiltBidIncludedInBlock(validator, fork, bid);

    // delayed
    asyncRunner.executeDueActions();

    verifyNoInteractions(validatorApiChannel, validatorLogger);

    timeProvider.advanceTimeBy(EXECUTION_PAYLOAD_DUTY_DELAY_FOR_SELF_BUILT_BID);

    // should execute now
    asyncRunner.executeDueActions();

    verify(validatorApiChannel).publishSignedExecutionPayload(signedExecutionPayload);
    verify(validatorLogger)
        .logExecutionPayloadDuty(
            eq(signedExecutionPayload.getMessage().getSlot()),
            eq(signedExecutionPayload.getMessage().getBuilderIndex()),
            eq(signedExecutionPayload.getMessage().getBeaconBlockRoot()),
            any());
  }

  @Test
  public void dutyFailureLogsAnError() {
    final ExecutionPayloadBid bid = dataStructureUtil.randomExecutionPayloadBid();

    final IllegalStateException exception = new IllegalStateException("oopsy");
    when(validatorApiChannel.createUnsignedExecutionPayload(bid.getSlot(), bid.getBuilderIndex()))
        .thenReturn(SafeFuture.failedFuture(exception));

    duty.onSelfBuiltBidIncludedInBlock(validator, fork, bid);

    timeProvider.advanceTimeBy(EXECUTION_PAYLOAD_DUTY_DELAY_FOR_SELF_BUILT_BID);
    asyncRunner.executeDueActions();

    verify(validatorLogger)
        .executionPayloadDutyFailed(
            eq(bid.getSlot()),
            eq(bid.getBuilderIndex()),
            argThat(throwable -> throwable.getCause().equals(exception)));
  }
}
