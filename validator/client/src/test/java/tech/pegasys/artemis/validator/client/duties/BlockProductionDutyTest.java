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

package tech.pegasys.artemis.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;
import static tech.pegasys.artemis.util.async.SafeFuture.failedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.bls.bls.BLSSignature;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.client.ForkProvider;
import tech.pegasys.artemis.validator.client.Validator;
import tech.pegasys.artemis.validator.client.signer.Signer;

class BlockProductionDutyTest {
  private static final UnsignedLong SLOT = UnsignedLong.valueOf(498294);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final Signer signer = mock(Signer.class);
  private final Validator validator = new Validator(dataStructureUtil.randomPublicKey(), signer);
  private final Fork fork = dataStructureUtil.randomFork();

  private final BlockProductionDuty duty =
      new BlockProductionDuty(validator, SLOT, forkProvider, validatorApiChannel);

  @BeforeEach
  public void setUp() {
    when(forkProvider.getFork()).thenReturn(completedFuture(fork));
  }

  @Test
  public void shouldCreateAndPublishBlock() {
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();
    final BeaconBlock unsignedBlock = dataStructureUtil.randomBeaconBlock(SLOT.longValue());
    when(signer.createRandaoReveal(compute_epoch_at_slot(SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(SLOT, randaoReveal))
        .thenReturn(completedFuture(Optional.of(unsignedBlock)));
    when(signer.signBlock(unsignedBlock, fork)).thenReturn(completedFuture(blockSignature));

    assertThat(duty.performDuty()).isCompleted();

    verify(validatorApiChannel)
        .sendSignedBlock(new SignedBeaconBlock(unsignedBlock, blockSignature));
  }

  @Test
  public void shouldFailWhenCreateRandaoFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    when(signer.createRandaoReveal(compute_epoch_at_slot(SLOT), fork))
        .thenReturn(failedFuture(error));

    assertDutyFails(error);
  }

  @Test
  public void shouldFailWhenCreateUnsignedBlockFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    when(signer.createRandaoReveal(compute_epoch_at_slot(SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(SLOT, randaoReveal))
        .thenReturn(failedFuture(error));

    assertDutyFails(error);
  }

  @Test
  public void shouldFailWhenCreateUnsignedBlockReturnsEmpty() {
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    when(signer.createRandaoReveal(compute_epoch_at_slot(SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(SLOT, randaoReveal))
        .thenReturn(completedFuture(Optional.empty()));

    assertThat(duty.performDuty()).isCompletedExceptionally();
  }

  @Test
  public void shouldFailWhenSigningBlockFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BeaconBlock unsignedBlock = dataStructureUtil.randomBeaconBlock(SLOT.longValue());
    when(signer.createRandaoReveal(compute_epoch_at_slot(SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(SLOT, randaoReveal))
        .thenReturn(completedFuture(Optional.of(unsignedBlock)));
    when(signer.signBlock(unsignedBlock, fork)).thenReturn(failedFuture(error));

    assertDutyFails(error);
  }

  public void assertDutyFails(final RuntimeException error) {
    final SafeFuture<?> result = duty.performDuty();
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(error);
  }
}
