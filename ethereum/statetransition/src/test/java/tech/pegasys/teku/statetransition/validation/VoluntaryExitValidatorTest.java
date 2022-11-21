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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.REJECT;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.VoluntaryExitValidator.ExitInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class VoluntaryExitValidatorTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 25);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final Spec mockSpec = mock(Spec.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private RecentChainData recentChainData;
  private BeaconChainUtil beaconChainUtil;

  private VoluntaryExitValidator voluntaryExitValidator;

  @BeforeEach
  void beforeEach() {
    recentChainData = MemoryOnlyRecentChainData.create(spec);
    beaconChainUtil = BeaconChainUtil.create(spec, recentChainData, VALIDATOR_KEYS, true);

    voluntaryExitValidator = new VoluntaryExitValidator(mockSpec, recentChainData);
  }

  @Test
  public void shouldAcceptValidVoluntaryExit() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(mockSpec.validateVoluntaryExit(getBestState(), exit)).thenReturn(Optional.empty());
    when(mockSpec.verifyVoluntaryExitSignature(getBestState(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);
    assertValidationResult(exit, ACCEPT);
  }

  @Test
  public void shouldIgnoreExitsAfterTheFirstForValidator() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);

    SignedVoluntaryExit exit1 = dataStructureUtil.randomSignedVoluntaryExit();
    SignedVoluntaryExit exit2 = new SignedVoluntaryExit(exit1.getMessage(), exit1.getSignature());
    SignedVoluntaryExit exit3 = new SignedVoluntaryExit(exit2.getMessage(), exit2.getSignature());

    when(mockSpec.validateVoluntaryExit(eq(getBestState()), any())).thenReturn(Optional.empty());
    when(mockSpec.verifyVoluntaryExitSignature(
            eq(getBestState()), any(), eq(BLSSignatureVerifier.SIMPLE)))
        .thenReturn(true);

    assertValidationResult(exit1, ACCEPT);
    assertValidationResult(exit2, IGNORE, "Exit is not the first one");
    assertValidationResult(exit3, IGNORE, "Exit is not the first one");
  }

  @Test
  public void shouldRejectInvalidExit() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(mockSpec.validateVoluntaryExit(getBestState(), exit))
        .thenReturn(Optional.of(ExitInvalidReason.exitInitiated()));
    when(mockSpec.verifyVoluntaryExitSignature(getBestState(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);

    assertValidationResult(exit, REJECT, "Validator has already initiated exit");
  }

  @Test
  public void shouldRejectExitWithInvalidSignature() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(mockSpec.validateVoluntaryExit(getBestState(), exit)).thenReturn(Optional.empty());
    when(mockSpec.verifyVoluntaryExitSignature(getBestState(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(false);

    assertValidationResult(exit, REJECT, "Signature is invalid");
  }

  private void assertValidationResult(
      final SignedVoluntaryExit exit, final ValidationResultCode expectedResultCode) {
    assertThat(voluntaryExitValidator.validateForGossip(exit))
        .isCompletedWithValueMatching(result -> result.code() == expectedResultCode);
  }

  private void assertValidationResult(
      final SignedVoluntaryExit exit,
      final ValidationResultCode expectedCode,
      final String description) {
    assertThat(voluntaryExitValidator.validateForGossip(exit))
        .isCompletedWithValueMatching(
            result ->
                result.code() == expectedCode
                    && result.getDescription().orElse("").contains(description));
  }

  private BeaconState getBestState() {
    final SafeFuture<BeaconState> stateFuture = recentChainData.getBestState().orElseThrow();
    assertThat(stateFuture).isCompleted();
    return stateFuture.join();
  }
}
