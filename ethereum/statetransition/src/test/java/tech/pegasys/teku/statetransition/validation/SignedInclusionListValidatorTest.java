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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.heze.util.InclusionListUtil;
import tech.pegasys.teku.spec.signatures.LocalSigner;
import tech.pegasys.teku.storage.client.RecentChainData;

class SignedInclusionListValidatorTest {

  private static final UInt64 HEZE_FORK_EPOCH = UInt64.valueOf(2);
  private static final int VALIDATOR_COUNT = 64;

  private final Spec spec = TestSpecFactory.createMinimalWithHezeForkEpoch(HEZE_FORK_EPOCH);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final SignedInclusionListValidator validator =
      new SignedInclusionListValidator(
          spec, recentChainData, AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));

  @Test
  void shouldValidateFirstHezeSlotWhenBlockInEffectIsPreFork() throws Exception {
    final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(VALIDATOR_COUNT);
    final ChainBuilder chainBuilder = ChainBuilder.create(spec, validatorKeys);
    final BeaconState genesisState = chainBuilder.generateGenesis().getState();
    final UInt64 firstHezeSlot = spec.computeStartSlotAtEpoch(HEZE_FORK_EPOCH);
    final BeaconState preHezeState = spec.processSlots(genesisState, firstHezeSlot.decrement());
    final SignedInclusionList signedInclusionList =
        createSignedInclusionList(validatorKeys, preHezeState, firstHezeSlot);

    assertThat(preHezeState.getFork()).isNotEqualTo(spec.fork(HEZE_FORK_EPOCH));
    when(recentChainData.retrieveStateInEffectAtSlot(firstHezeSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(preHezeState)));

    assertThatSafeFuture(validator.validate(signedInclusionList, new TreeMap<>()))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  private SignedInclusionList createSignedInclusionList(
      final List<BLSKeyPair> validatorKeys, final BeaconState state, final UInt64 slot) {
    final InclusionListUtil inclusionListUtil =
        spec.atSlot(slot).getInclusionListUtil().orElseThrow();
    final int validatorIndex = inclusionListUtil.getInclusionListCommittee(state, slot).getInt(0);
    final InclusionList inclusionList =
        spec.atSlot(slot)
            .getSchemaDefinitions()
            .toVersionHeze()
            .orElseThrow()
            .getInclusionListSchema()
            .create(
                slot,
                UInt64.valueOf(validatorIndex),
                inclusionListUtil.getInclusionListCommitteeRoot(state, slot),
                List.of());
    final ForkInfo forkInfo =
        new ForkInfo(spec.fork(spec.computeEpochAtSlot(slot)), state.getGenesisValidatorsRoot());
    final BLSSignature signature =
        new LocalSigner(spec, validatorKeys.get(validatorIndex), SyncAsyncRunner.SYNC_RUNNER)
            .signInclusionList(inclusionList, forkInfo)
            .join();
    return spec.atSlot(slot)
        .getSchemaDefinitions()
        .toVersionHeze()
        .orElseThrow()
        .getSignedInclusionListSchema()
        .create(inclusionList, signature);
  }
}
