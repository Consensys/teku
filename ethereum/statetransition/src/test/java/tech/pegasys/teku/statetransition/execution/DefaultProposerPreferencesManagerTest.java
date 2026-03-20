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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ProposerPreferencesGossipValidator;

@TestSpecContext(milestone = SpecMilestone.GLOAS)
public class DefaultProposerPreferencesManagerTest {

  private final ProposerPreferencesGossipValidator gossipValidator =
      mock(ProposerPreferencesGossipValidator.class);

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<SignedProposerPreferences> subscriber =
      mock(OperationAddedSubscriber.class);

  private DefaultProposerPreferencesManager manager;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
    manager = new DefaultProposerPreferencesManager(gossipValidator);
    manager.subscribeOperationAdded(subscriber);
  }

  @TestTemplate
  void shouldStoreAndReturnAcceptedPreferences() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();
    final UInt64 slot = signedProposerPreferences.getMessage().getProposalSlot();

    when(gossipValidator.validate(signedProposerPreferences))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    safeJoin(manager.addLocal(signedProposerPreferences));

    assertThat(manager.getProposerPreferences(slot))
        .hasValue(signedProposerPreferences.getMessage());
  }

  @TestTemplate
  void shouldNotStoreRejectedPreferences() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();
    final UInt64 slot = signedProposerPreferences.getMessage().getProposalSlot();

    when(gossipValidator.validate(signedProposerPreferences))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("bad signature")));

    safeJoin(manager.addLocal(signedProposerPreferences));

    assertThat(manager.getProposerPreferences(slot)).isEmpty();
  }

  @TestTemplate
  void shouldNotifySubscriberOnAcceptWithFromNetworkFalseForLocal() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();

    when(gossipValidator.validate(signedProposerPreferences))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    safeJoin(manager.addLocal(signedProposerPreferences));

    verify(subscriber).onOperationAdded(eq(signedProposerPreferences), eq(ACCEPT), eq(false));
  }

  @TestTemplate
  void shouldNotifySubscriberOnAcceptWithFromNetworkTrueForRemote() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();

    when(gossipValidator.validate(signedProposerPreferences))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    safeJoin(manager.addRemote(signedProposerPreferences));

    verify(subscriber).onOperationAdded(eq(signedProposerPreferences), eq(ACCEPT), eq(true));
  }

  @TestTemplate
  void shouldNotNotifySubscriberOnReject() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();

    when(gossipValidator.validate(signedProposerPreferences))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("bad signature")));

    safeJoin(manager.addLocal(signedProposerPreferences));

    verify(subscriber, never()).onOperationAdded(any(), any(), eq(false));
  }

  @TestTemplate
  void shouldReturnEmptyForUnknownSlot() {
    assertThat(manager.getProposerPreferences(UInt64.valueOf(999))).isEmpty();
  }
}
