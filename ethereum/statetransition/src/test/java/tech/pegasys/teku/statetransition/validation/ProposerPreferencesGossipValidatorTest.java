/*
 * Copyright Consensys Software Inc., 2026
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

@TestSpecContext(milestone = {SpecMilestone.GLOAS})
public class ProposerPreferencesGossipValidatorTest {

  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ProposerPreferencesGossipValidator validator;

  private BeaconState state;
  private UInt64 proposalSlot;
  private UInt64 validatorIndex;
  private SignedProposerPreferences signedProposerPreferences;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();

    validator =
        new ProposerPreferencesGossipValidator(spec, gossipValidationHelper, recentChainData);

    state = dataStructureUtil.randomBeaconState();
    final BeaconStateGloas gloasState = BeaconStateGloas.required(state);

    // First entry of the next epoch portion of the proposer lookahead
    final int slotsPerEpoch = spec.atSlot(state.getSlot()).getConfig().getSlotsPerEpoch();
    validatorIndex = gloasState.getProposerLookahead().getElement(slotsPerEpoch);

    final UInt64 currentEpoch = spec.computeEpochAtSlot(state.getSlot());
    proposalSlot = spec.computeStartSlotAtEpoch(currentEpoch.plus(1));

    signedProposerPreferences = createSignedProposerPreferences(proposalSlot, validatorIndex);

    when(gossipValidationHelper.isSlotInNextEpoch(proposalSlot)).thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(true);
    when(recentChainData.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));
  }

  @TestTemplate
  void shouldAccept() {
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnore_whenSlotNotInNextEpoch() {
    when(gossipValidationHelper.isSlotInNextEpoch(proposalSlot)).thenReturn(false);
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnore_whenAlreadySeen() {
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(ACCEPT);

    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldReject_whenValidatorIndexDoesNotMatchLookahead() {
    final UInt64 wrongValidatorIndex = validatorIndex.plus(9999);
    final SignedProposerPreferences wrongIndexPreferences =
        createSignedProposerPreferences(proposalSlot, wrongValidatorIndex);

    assertThatSafeFuture(validator.validate(wrongIndexPreferences))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldReject_whenSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(false);

    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldNotMarkAsSeenIfValidationFails() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(false);

    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    // Fix the signature mock
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(true);

    // Should accept now, not ignore
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnore_whenDuplicateArrivesWhileValidating() {
    // Create a slow state future
    final SafeFuture<BeaconState> slowStateFuture = new SafeFuture<>();
    when(recentChainData.getBestState()).thenReturn(Optional.of(slowStateFuture));

    // Start first validation (will block on state)
    final SafeFuture<InternalValidationResult> firstResult =
        validator.validate(signedProposerPreferences);

    // Reset to return completed state for second validation
    when(recentChainData.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));

    // Second validation completes first
    final SafeFuture<InternalValidationResult> secondResult =
        validator.validate(signedProposerPreferences);
    assertThatSafeFuture(secondResult).isCompletedWithValue(ACCEPT);

    // Now complete first validation - should be ignored due to race condition
    slowStateFuture.complete(state);
    assertThatSafeFuture(firstResult)
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  private SignedProposerPreferences createSignedProposerPreferences(
      final UInt64 proposalSlot, final UInt64 validatorIndex) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(proposalSlot).getSchemaDefinitions());
    final ProposerPreferencesSchema proposerPreferencesSchema =
        schemaDefinitions.getProposerPreferencesSchema();
    final SignedProposerPreferencesSchema signedProposerPreferencesSchema =
        schemaDefinitions.getSignedProposerPreferencesSchema();

    final ProposerPreferences proposerPreferences =
        proposerPreferencesSchema.create(
            proposalSlot,
            validatorIndex,
            dataStructureUtil.randomEth1Address(),
            dataStructureUtil.randomUInt64());

    return signedProposerPreferencesSchema.create(
        proposerPreferences, dataStructureUtil.randomSignature());
  }
}
