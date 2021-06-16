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

package tech.pegasys.teku.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;

import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

class BlockProductionDutyTest {
  private static final String TYPE = "block";
  private static final UInt64 SLOT = UInt64.valueOf(498294);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final Signer signer = mock(Signer.class);
  private final Bytes32 graffiti = dataStructureUtil.randomBytes32();
  private final Validator validator =
      new Validator(
          dataStructureUtil.randomPublicKey(),
          signer,
          new FileBackedGraffitiProvider(Optional.of(graffiti), Optional.empty()));
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);

  private final BlockProductionDuty duty =
      new BlockProductionDuty(validator, SLOT, forkProvider, validatorApiChannel, spec);

  @BeforeEach
  public void setUp() {
    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
  }

  @Test
  public void shouldCreateAndPublishBlock() {
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();
    final BeaconBlock unsignedBlock = dataStructureUtil.randomBeaconBlock(SLOT.longValue());
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(SLOT, randaoReveal, Optional.of(graffiti)))
        .thenReturn(completedFuture(Optional.of(unsignedBlock)));
    when(signer.signBlock(unsignedBlock, fork)).thenReturn(completedFuture(blockSignature));
    final SignedBeaconBlock signedBlock =
        dataStructureUtil.signedBlock(unsignedBlock, blockSignature);
    when(validatorApiChannel.sendSignedBlock(signedBlock))
        .thenReturn(completedFuture(SendSignedBlockResult.success(signedBlock.getRoot())));

    performAndReportDuty();

    verify(validatorApiChannel).sendSignedBlock(signedBlock);
    verify(validatorLogger).dutyCompleted(TYPE, SLOT, 1, Set.of(unsignedBlock.hashTreeRoot()));
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  public void shouldFailWhenCreateRandaoFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(SLOT), fork))
        .thenReturn(failedFuture(error));

    assertDutyFails(error);
  }

  @Test
  public void shouldFailWhenCreateUnsignedBlockFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(SLOT, randaoReveal, Optional.of(graffiti)))
        .thenReturn(failedFuture(error));

    assertDutyFails(error);
  }

  @Test
  public void shouldFailWhenCreateUnsignedBlockReturnsEmpty() {
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(SLOT, randaoReveal, Optional.of(graffiti)))
        .thenReturn(completedFuture(Optional.empty()));

    performAndReportDuty();

    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(SLOT),
            eq(Set.of(validator.getPublicKey().toAbbreviatedString())),
            any(IllegalStateException.class));
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  public void shouldFailWhenSigningBlockFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BeaconBlock unsignedBlock = dataStructureUtil.randomBeaconBlock(SLOT.longValue());
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(SLOT, randaoReveal, Optional.of(graffiti)))
        .thenReturn(completedFuture(Optional.of(unsignedBlock)));
    when(signer.signBlock(unsignedBlock, fork)).thenReturn(failedFuture(error));

    assertDutyFails(error);
  }

  public void assertDutyFails(final RuntimeException error) {
    performAndReportDuty();
    verify(validatorLogger)
        .dutyFailed(TYPE, SLOT, Set.of(validator.getPublicKey().toAbbreviatedString()), error);
    verifyNoMoreInteractions(validatorLogger);
  }

  private void performAndReportDuty() {
    final SafeFuture<DutyResult> result = duty.performDuty();
    assertThat(result).isCompleted();
    result.join().report(TYPE, SLOT, validatorLogger);
  }
}
