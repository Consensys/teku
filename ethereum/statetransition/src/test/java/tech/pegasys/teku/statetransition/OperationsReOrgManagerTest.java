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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.SingleThreadedForkChoiceExecutor;
import tech.pegasys.teku.storage.api.TrackingReorgEventChannel;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    // Create initial fork
    ChainBuilder.BlockOptions fork1block1options = ChainBuilder.BlockOptions.create();
    final Attestation fork1block1attestation = chainBuilder.streamValidAttestationsWithTargetBlock(commonAncestor)
            .findFirst().get();
    fork1block1options.addAttestation(fork1block1attestation);
    SignedBlockAndState fork1block1 = chainBuilder.generateBlockAtSlot(11, fork1block1options);
    chainUpdater.saveBlock(fork1block1);

    ChainBuilder.BlockOptions fork1block2options = ChainBuilder.BlockOptions.create();
    final Attestation fork1block2attestation = chainBuilder.streamValidAttestationsWithTargetBlock(commonAncestor)
            .findFirst().get();
    fork1block1options.addAttestation(fork1block2attestation);
    SignedBlockAndState fork1block2 = chainBuilder.generateBlockAtSlot(12, fork1block2options);
    chainUpdater.saveBlock(fork1block2);

    ChainBuilder.BlockOptions fork1block3options = ChainBuilder.BlockOptions.create();
    final Attestation fork1block3attestation = chainBuilder.streamValidAttestationsWithTargetBlock(commonAncestor)
            .findFirst().get();
    fork1block1options.addAttestation(fork1block3attestation);
    SignedBlockAndState fork1block3 = chainBuilder.generateBlockAtSlot(13, fork1block3options);
    chainUpdater.saveBlock(fork1block3);
    forkChoice.processHead();

    // Create second fork
    ChainBuilder.BlockOptions fork2block1options = ChainBuilder.BlockOptions.create();
    final Attestation fork2block1attestation = newChainBuilder.streamValidAttestationsWithTargetBlock(commonAncestor)
            .findFirst().get();
    fork1block1options.addAttestation(fork2block1attestation);
    SignedBlockAndState fork2block1 = newChainBuilder.generateBlockAtSlot(11, fork2block1options);
    newChainUpdater.saveBlock(fork2block1);

    ChainBuilder.BlockOptions fork2block2options = ChainBuilder.BlockOptions.create();
    final Attestation fork2block2attestation = newChainBuilder.streamValidAttestationsWithTargetBlock(commonAncestor)
            .findFirst().get();
    fork1block1options.addAttestation(fork2block2attestation);
    SignedBlockAndState fork2block2 = newChainBuilder.generateBlockAtSlot(12, fork2block2options);
    newChainUpdater.saveBlock(fork2block2);

    ChainBuilder.BlockOptions fork2block3options = ChainBuilder.BlockOptions.create();
    final Attestation fork2block3attestation = newChainBuilder.streamValidAttestationsWithTargetBlock(commonAncestor)
            .findFirst().get();
    fork1block1options.addAttestation(fork2block3attestation);
    SignedBlockAndState fork2block3 = newChainBuilder.generateBlockAtSlot(13, fork2block3options);
    newChainUpdater.saveBlock(fork2block3);

    ChainBuilder.BlockOptions fork2block4options = ChainBuilder.BlockOptions.create();
    final Attestation fork2block4attestation = newChainBuilder.streamValidAttestationsWithTargetBlock(commonAncestor)
            .findFirst().get();
    fork1block1options.addAttestation(fork2block4attestation);
    SignedBlockAndState fork2block4 = chainBuilder.generateBlockAtSlot(14, fork2block4options);
    newChainUpdater.saveBlock(fork2block4);
    forkChoice.processHead();

    assertThat(reorgEventChannel.getReorgEvents())
        .contains(
            new TrackingReorgEventChannel.ReorgEvent(
                fork2block4.getRoot(),
                fork2block4.getSlot(),
                fork1block3.getRoot(),
                commonAncestor.getSlot()));

      operationsReOrgManager.reorgOccurred(
              fork2block4.getRoot(),
              fork2block4.getSlot(),
              fork1block3.getRoot(),
              commonAncestor.getSlot());

    BeaconBlockBody firstBlockBody = fork2block4.getBlock().getMessage().getBody();

    verify(proposerSlashingOperationPool).addAll(firstBlockBody.getProposer_slashings());
    verify(attesterSlashingOperationPool).addAll(firstBlockBody.getAttester_slashings());
    verify(exitOperationPool).addAll(firstBlockBody.getVoluntary_exits());

//    firstBlockBody
//        .getAttestations()
//        .forEach(
//            a ->
//                verify(attestationManager)
//                    .onAttestation(ValidateableAttestation.fromAttestation(a)));
  }
}
