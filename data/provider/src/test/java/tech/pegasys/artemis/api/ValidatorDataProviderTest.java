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

package tech.pegasys.artemis.api;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.api.schema.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class ValidatorDataProviderTest {
  private final ValidatorCoordinator validatorCoordinator = mock(ValidatorCoordinator.class);
  private ValidatorDataProvider provider = new ValidatorDataProvider(validatorCoordinator);;
  private final tech.pegasys.artemis.datastructures.blocks.BeaconBlock blockInternal =
      DataStructureUtil.randomBeaconBlock(123, 456);
  private final BeaconBlock block = new BeaconBlock(blockInternal);

  @Test
  void getUnsignedBeaconBlockAtSlot_throwsWithoutSlotDefined() {
    assertThrows(
        IllegalArgumentException.class, () -> provider.getUnsignedBeaconBlockAtSlot(null, null));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowWithoutRandaoDefined() {
    assertThrows(
        IllegalArgumentException.class, () -> provider.getUnsignedBeaconBlockAtSlot(ONE, null));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldCreateAnUnsignedBlock()
      throws SlotProcessingException, EpochProcessingException, StateTransitionException {
    tech.pegasys.artemis.util.bls.BLSSignature signatureInternal =
        tech.pegasys.artemis.util.bls.BLSSignature.random();
    BLSSignature signature = new BLSSignature(signatureInternal);
    when(validatorCoordinator.createUnsignedBlock(ONE, signatureInternal))
        .thenReturn(Optional.of(blockInternal));

    Optional<BeaconBlock> data = provider.getUnsignedBeaconBlockAtSlot(ONE, signature);
    verify(validatorCoordinator).createUnsignedBlock(ONE, signatureInternal);
    assertThat(data.isPresent()).isTrue();
    assertThat(data.get()).usingRecursiveComparison().isEqualTo(block);
  }
}
