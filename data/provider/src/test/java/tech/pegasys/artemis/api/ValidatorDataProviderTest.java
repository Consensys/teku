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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.api.schema.Attestation;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.api.schema.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class ValidatorDataProviderTest {

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<tech.pegasys.artemis.datastructures.operations.Attestation> args =
      ArgumentCaptor.forClass(tech.pegasys.artemis.datastructures.operations.Attestation.class);

  private final ValidatorCoordinator validatorCoordinator = mock(ValidatorCoordinator.class);
  private ValidatorDataProvider provider = new ValidatorDataProvider(validatorCoordinator);;
  private final tech.pegasys.artemis.datastructures.blocks.BeaconBlock blockInternal =
      DataStructureUtil.randomBeaconBlock(123, 456);
  private final BeaconBlock block = new BeaconBlock(blockInternal);
  private final tech.pegasys.artemis.util.bls.BLSSignature signatureInternal =
      tech.pegasys.artemis.util.bls.BLSSignature.random(1234);
  private final BLSSignature signature = new BLSSignature(signatureInternal);

  @Test
  void getUnsignedBeaconBlockAtSlot_throwsWithoutSlotDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(null, null));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowWithoutRandaoDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(ONE, null));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowDataProviderExceptionIfStateTransitionException() {
    shouldThrowDataProviderExceptionAfterGettingException(new StateTransitionException(null));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowDataProviderExceptionIfSlotProcessingException() {
    shouldThrowDataProviderExceptionAfterGettingException(new SlotProcessingException("TEST"));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowDataProviderExceptionIfEpochProcessingException() {
    shouldThrowDataProviderExceptionAfterGettingException(new EpochProcessingException("TEST"));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldCreateAnUnsignedBlock()
      throws SlotProcessingException, EpochProcessingException, StateTransitionException {
    when(validatorCoordinator.createUnsignedBlock(ONE, signatureInternal))
        .thenReturn(Optional.of(blockInternal));

    Optional<BeaconBlock> data = provider.getUnsignedBeaconBlockAtSlot(ONE, signature);
    verify(validatorCoordinator).createUnsignedBlock(ONE, signatureInternal);
    assertThat(data.isPresent()).isTrue();
    assertThat(data.get()).usingRecursiveComparison().isEqualTo(block);
  }

  private void shouldThrowDataProviderExceptionAfterGettingException(Exception ex) {
    tech.pegasys.artemis.util.bls.BLSSignature signatureInternal =
        tech.pegasys.artemis.util.bls.BLSSignature.random(1234);
    BLSSignature signature = new BLSSignature(signatureInternal);
    try {
      when(validatorCoordinator.createUnsignedBlock(ONE, signatureInternal)).thenThrow(ex);
    } catch (Exception ignored) {
    }

    assertThatExceptionOfType(DataProviderException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(ONE, signature));
  }

  @Test
  void submitAttestation_shouldSubmitAnInternalAttestationStructure() {
    tech.pegasys.artemis.datastructures.operations.Attestation internalAttestation =
        DataStructureUtil.randomAttestation(1234);
    Attestation attestation = new Attestation(internalAttestation);

    provider.submitAttestation(attestation);

    verify(validatorCoordinator).postSignedAttestation(args.capture(), eq(true));
    assertThat(args.getValue()).usingRecursiveComparison().isEqualTo(internalAttestation);
  }
}
