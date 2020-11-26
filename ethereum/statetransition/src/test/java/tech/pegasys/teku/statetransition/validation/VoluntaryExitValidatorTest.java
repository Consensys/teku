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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.core.operationsignatureverifiers.VoluntaryExitSignatureVerifier;
import tech.pegasys.teku.core.operationvalidators.VoluntaryExitStateTransitionValidator;
import tech.pegasys.teku.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class VoluntaryExitValidatorTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 25);
  private DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private RecentChainData recentChainData;
  private BeaconChainUtil beaconChainUtil;

  private VoluntaryExitValidator voluntaryExitValidator;
  private VoluntaryExitStateTransitionValidator stateTransitionValidator;
  private VoluntaryExitSignatureVerifier signatureVerifier;

  @BeforeEach
  void beforeEach() {
    recentChainData = MemoryOnlyRecentChainData.create(new EventBus());
    beaconChainUtil = BeaconChainUtil.create(recentChainData, VALIDATOR_KEYS, true);

    stateTransitionValidator = mock(VoluntaryExitStateTransitionValidator.class);
    signatureVerifier = mock(VoluntaryExitSignatureVerifier.class);
    voluntaryExitValidator =
        new VoluntaryExitValidator(recentChainData, stateTransitionValidator, signatureVerifier);
  }

  @Test
  public void shouldAcceptValidVoluntaryExit() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(stateTransitionValidator.validate(recentChainData.getBestState().orElseThrow(), exit))
        .thenReturn(Optional.empty());
    when(signatureVerifier.verifySignature(
            recentChainData.getBestState().orElseThrow(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);
    assertThat(voluntaryExitValidator.validateFully(exit)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldIgnoreExitsAfterTheFirstForValidator() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);

    SignedVoluntaryExit exit1 = dataStructureUtil.randomSignedVoluntaryExit();
    SignedVoluntaryExit exit2 = new SignedVoluntaryExit(exit1.getMessage(), exit1.getSignature());
    SignedVoluntaryExit exit3 = new SignedVoluntaryExit(exit2.getMessage(), exit2.getSignature());

    when(stateTransitionValidator.validate(eq(recentChainData.getBestState().orElseThrow()), any()))
        .thenReturn(Optional.empty());
    when(signatureVerifier.verifySignature(
            eq(recentChainData.getBestState().orElseThrow()),
            any(),
            eq(BLSSignatureVerifier.SIMPLE)))
        .thenReturn(true);

    assertThat(voluntaryExitValidator.validateFully(exit1)).isEqualTo(ACCEPT);
    assertThat(voluntaryExitValidator.validateFully(exit2)).isEqualTo(IGNORE);
    assertThat(voluntaryExitValidator.validateFully(exit3)).isEqualTo(IGNORE);
  }

  @Test
  public void shouldRejectInvalidExit() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(stateTransitionValidator.validate(recentChainData.getBestState().orElseThrow(), exit))
        .thenReturn(
            Optional.of(VoluntaryExitStateTransitionValidator.ExitInvalidReason.EXIT_INITIATED));
    when(signatureVerifier.verifySignature(
            recentChainData.getBestState().orElseThrow(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);
    assertThat(voluntaryExitValidator.validateFully(exit)).isEqualTo(REJECT);
  }

  @Test
  public void shouldRejectExitWithInvalidSignature() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(stateTransitionValidator.validate(recentChainData.getBestState().orElseThrow(), exit))
        .thenReturn(Optional.empty());
    when(signatureVerifier.verifySignature(
            recentChainData.getBestState().orElseThrow(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(false);
    assertThat(voluntaryExitValidator.validateFully(exit)).isEqualTo(REJECT);
  }
}
