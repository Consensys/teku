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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionAndPublishingPerformanceFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

@TestSpecContext(milestone = SpecMilestone.GLOAS)
class ValidatorApiHandlerGloasTest {

  private final ProposerPreferencesManager proposerPreferencesManager =
      mock(ProposerPreferencesManager.class);

  private DataStructureUtil dataStructureUtil;
  private ValidatorApiHandlerGloas handler;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    final Spec spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();

    handler =
        new ValidatorApiHandlerGloas(
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            spec,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            new BlockProductionAndPublishingPerformanceFactory(
                StubTimeProvider.withTimeInMillis(0),
                __ -> UInt64.ZERO,
                false,
                0,
                0,
                0,
                0,
                Optional.empty()),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            proposerPreferencesManager);
  }

  @TestTemplate
  void sendSignedProposerPreferences_shouldValidateViaManager() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();

    when(proposerPreferencesManager.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<Void> result =
        handler.sendSignedProposerPreferences(List.of(signedProposerPreferences));

    assertThat(result).isCompleted();
    verify(proposerPreferencesManager).addLocal(signedProposerPreferences);
  }

  @TestTemplate
  void sendSignedProposerPreferences_shouldCompleteWhenRejected() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();

    when(proposerPreferencesManager.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("bad signature")));

    final SafeFuture<Void> result =
        handler.sendSignedProposerPreferences(List.of(signedProposerPreferences));

    assertThat(result).isCompleted();
  }

  @TestTemplate
  void sendSignedProposerPreferences_shouldHandleEmptyList() {
    final SafeFuture<Void> result = handler.sendSignedProposerPreferences(List.of());

    assertThat(result).isCompleted();
    verify(proposerPreferencesManager, never()).addLocal(any());
  }
}
