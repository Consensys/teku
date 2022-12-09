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

package tech.pegasys.teku.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.validator.api.SubmitDataError;

@SuppressWarnings("unchecked")
public class NodeDataProviderTest {
  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);

  private final BlockManager blockManager = mock(BlockManager.class);
  private final AttestationManager attestationManager = mock(AttestationManager.class);
  private final ActiveValidatorChannel validatorChannel = mock(ActiveValidatorChannel.class);
  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);

  private OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);

  private OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);

  private OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);

  private OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool =
      mock(OperationPool.class);

  private SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  private NodeDataProvider provider;

  @BeforeEach
  public void setup() {
    provider =
        new NodeDataProvider(
            attestationPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool,
            blsToExecutionChangePool,
            syncCommitteeContributionPool,
            blockManager,
            attestationManager,
            false,
            validatorChannel,
            proposersDataManager);
  }

  @Test
  void shouldAddBlsToExecutionChangesToPool() throws ExecutionException, InterruptedException {
    when(blsToExecutionChangePool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final SafeFuture<List<SubmitDataError>> future =
        provider.postBlsToExecutionChanges(
            List.of(
                dataStructureUtil.randomSignedBlsToExecutionChange(),
                dataStructureUtil.randomSignedBlsToExecutionChange()));
    assertThat(future).isCompleted();
    assertThat(future.get()).isEqualTo(List.of());
  }

  @Test
  void blsToExecutionChanges_ReturnsListOfErrors() throws ExecutionException, InterruptedException {
    when(blsToExecutionChangePool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT))
        .thenReturn(
            SafeFuture.completedFuture(InternalValidationResult.reject("Computer says no")));
    final SafeFuture<List<SubmitDataError>> future =
        provider.postBlsToExecutionChanges(
            List.of(
                dataStructureUtil.randomSignedBlsToExecutionChange(),
                dataStructureUtil.randomSignedBlsToExecutionChange()));
    assertThat(future).isCompleted();
    assertThat(future.get())
        .isEqualTo(List.of(new SubmitDataError(UInt64.ONE, "Computer says no")));
  }
}
