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

package tech.pegasys.teku.statetransition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.SingleThreadedForkChoiceExecutor;
import tech.pegasys.teku.storage.api.TrackingReorgEventChannel;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class OperationsReOrgManagerTest {

  private OperationPool<ProposerSlashing> proposerSlashingOperationPool = mock(OperationPool.class);
  private OperationPool<AttesterSlashing> attesterSlashingOperationPool = mock(OperationPool.class);
  private OperationPool<SignedVoluntaryExit> exitOperationPool = mock(OperationPool.class);
  private AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private AttestationManager attestationManager = mock(AttestationManager.class);

  @Test
  void test() throws Exception {
    StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    RecentChainData recentChainData = storageSystem.recentChainData();
    TrackingReorgEventChannel reorgEventChannel = storageSystem.reorgEventChannel();

    OperationsReOrgManager operationsReOrgManager =
        new OperationsReOrgManager(
            proposerSlashingOperationPool,
            attesterSlashingOperationPool,
            exitOperationPool,
            attestationPool,
            attestationManager,
            recentChainData);

    ForkChoice forkChoice =
        new ForkChoice(
            SingleThreadedForkChoiceExecutor.create(), recentChainData, new StateTransition());

    ChainUpdater chainUpdater = storageSystem.chainUpdater();
    ChainBuilder chainBuilder = storageSystem.chainBuilder();

    chainUpdater.initializeGenesis();
    SignedBlockAndState commonAncestor = chainUpdater.advanceChain(10);
    ChainBuilder newChainBuilder = chainBuilder.fork();
    ChainUpdater newChainUpdater = new ChainUpdater(recentChainData, newChainBuilder);

    // Create one fork with no attestations
    SignedBlockAndState initialCanonicalChainBlock = chainBuilder.generateBlockAtSlot(20);
    chainUpdater.saveBlock(initialCanonicalChainBlock);
    forkChoice.processHead();

    ChainBuilder.BlockOptions options = ChainBuilder.BlockOptions.create();
    final Attestation attestation1 =
        newChainBuilder
            .streamValidAttestationsWithTargetBlock(commonAncestor)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to create attestation for block "
                            + commonAncestor.getBlock().getRoot()
                            + " chain head: "
                            + chainBuilder.getLatestBlockAndState().getRoot()
                            + " validators: "
                            + chainBuilder.getValidatorKeys().stream()
                                .map(BLSKeyPair::getPublicKey)
                                .map(BLSPublicKey::toString)
                                .collect(Collectors.joining(", "))));
    options.addAttestation(attestation1);
    SignedBlockAndState forkChainFirstBlock =
        newChainBuilder.generateBlockAtSlot(UInt64.valueOf(15), options);
    newChainUpdater.saveBlock(forkChainFirstBlock);

    final Attestation attestation =
        newChainBuilder
            .streamValidAttestationsWithTargetBlock(forkChainFirstBlock)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to create attestation for block "
                            + forkChainFirstBlock.getBlock().getRoot()
                            + " chain head: "
                            + chainBuilder.getLatestBlockAndState().getRoot()
                            + " validators: "
                            + chainBuilder.getValidatorKeys().stream()
                                .map(BLSKeyPair::getPublicKey)
                                .map(BLSPublicKey::toString)
                                .collect(Collectors.joining(", "))));
    forkChoice.onAttestation(ValidateableAttestation.fromAttestation(attestation));
    newChainUpdater.saveBlock(forkChainFirstBlock);
    forkChoice.processHead();

    // for some reason the reorg event common ancestor slot is 14 instead of 10. seems wrong.
    // probably due the test infrastructure not being up to date with the new efficient
    // .findCommonAncestor slot changes
    assertThat(reorgEventChannel.getReorgEvents())
        .contains(
            new TrackingReorgEventChannel.ReorgEvent(
                forkChainFirstBlock.getRoot(),
                forkChainFirstBlock.getSlot(),
                commonAncestor.getRoot(),
                UInt64.valueOf(10)));
    //  operationsReOrgManager.reorgOccurred(forkChainFirstBlock.getRoot(),
    // forkChainFirstBlock.getSlot(), commonAncestor.getRoot(), UInt64.valueOf(10));

    BeaconBlockBody firstBlockBody = forkChainFirstBlock.getBlock().getMessage().getBody();

    //  verify(proposerSlashingOperationPool).addAll(firstBlockBody.getProposer_slashings());
    //  verify(attesterSlashingOperationPool).addAll(firstBlockBody.getAttester_slashings());
    //  verify(exitOperationPool).addAll(firstBlockBody.getVoluntary_exits());
    firstBlockBody
        .getAttestations()
        .forEach(
            a ->
                verify(attestationManager, times(100))
                    .onAttestation(ValidateableAttestation.fromAttestation(a)));
  }
}
