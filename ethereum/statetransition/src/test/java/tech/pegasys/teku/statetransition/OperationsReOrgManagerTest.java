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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.client.RecentChainData;

@SuppressWarnings("unchecked")
public class OperationsReOrgManagerTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final OperationPool<ProposerSlashing> proposerSlashingOperationPool =
      mock(OperationPool.class);
  private final OperationPool<AttesterSlashing> attesterSlashingOperationPool =
      mock(OperationPool.class);
  private final OperationPool<SignedVoluntaryExit> exitOperationPool = mock(OperationPool.class);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final AttestationManager attestationManager = mock(AttestationManager.class);

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final OperationsReOrgManager operationsReOrgManager =
      new OperationsReOrgManager(
          proposerSlashingOperationPool,
          attesterSlashingOperationPool,
          exitOperationPool,
          attestationPool,
          attestationManager,
          recentChainData);

  @Test
  void shouldRequeueAndRemoveOperations() {
    BeaconBlock fork1Block1 = dataStructureUtil.randomBeaconBlock(10);
    BeaconBlock fork1Block2 = dataStructureUtil.randomBeaconBlock(11);

    BeaconBlock fork2Block1 = dataStructureUtil.randomBeaconBlock(12);
    BeaconBlock fork2Block2 = dataStructureUtil.randomBeaconBlock(13);

    UInt64 commonAncestorSlot = UInt64.valueOf(9);

    NavigableMap<UInt64, Bytes32> nowNotCanonicalBlockRoots = new TreeMap<>();
    nowNotCanonicalBlockRoots.put(UInt64.valueOf(10), fork1Block1.hashTreeRoot());
    nowNotCanonicalBlockRoots.put(UInt64.valueOf(11), fork1Block2.hashTreeRoot());
    when(recentChainData.getAncestorsOnFork(commonAncestorSlot, fork1Block2.hashTreeRoot()))
        .thenReturn(nowNotCanonicalBlockRoots);

    NavigableMap<UInt64, Bytes32> nowCanonicalBlockRoots = new TreeMap<>();
    nowCanonicalBlockRoots.put(UInt64.valueOf(12), fork2Block1.hashTreeRoot());
    nowCanonicalBlockRoots.put(UInt64.valueOf(13), fork2Block2.hashTreeRoot());
    when(recentChainData.getAncestorsOnFork(commonAncestorSlot, fork2Block2.hashTreeRoot()))
        .thenReturn(nowCanonicalBlockRoots);

    when(recentChainData.retrieveBlockByRoot(fork1Block1.hashTreeRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(fork1Block1)));
    when(recentChainData.retrieveBlockByRoot(fork1Block2.hashTreeRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(fork1Block2)));

    when(recentChainData.retrieveBlockByRoot(fork2Block1.hashTreeRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(fork2Block1)));
    when(recentChainData.retrieveBlockByRoot(fork2Block2.hashTreeRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(fork2Block2)));

    when(attestationManager.onAttestation(any()))
        .thenReturn(SafeFuture.completedFuture(AttestationProcessingResult.SUCCESSFUL));

    operationsReOrgManager.chainHeadUpdated(
        UInt64.valueOf(13),
        fork2Block2.getStateRoot(),
        fork2Block2.hashTreeRoot(),
        false,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        ReorgContext.of(
            fork1Block2.hashTreeRoot(),
            dataStructureUtil.randomUInt64(),
            fork1Block2.getStateRoot(),
            commonAncestorSlot,
            dataStructureUtil.randomBytes32()));

    verify(recentChainData).getAncestorsOnFork(commonAncestorSlot, fork1Block2.hashTreeRoot());

    verify(attestationPool).onReorg(commonAncestorSlot);
    verify(proposerSlashingOperationPool).addAll(fork1Block1.getBody().getProposerSlashings());
    verify(attesterSlashingOperationPool).addAll(fork1Block1.getBody().getAttesterSlashings());
    verify(exitOperationPool).addAll(fork1Block1.getBody().getVoluntaryExits());

    verify(proposerSlashingOperationPool).addAll(fork1Block2.getBody().getProposerSlashings());
    verify(attesterSlashingOperationPool).addAll(fork1Block2.getBody().getAttesterSlashings());
    verify(exitOperationPool).addAll(fork1Block2.getBody().getVoluntaryExits());

    ArgumentCaptor<ValidateableAttestation> argument =
        ArgumentCaptor.forClass(ValidateableAttestation.class);
    verify(attestationManager, atLeastOnce()).onAttestation(argument.capture());

    List<ValidateableAttestation> attestationList = new ArrayList<>();
    attestationList.addAll(
        fork1Block1.getBody().getAttestations().stream()
            .map(attestation -> ValidateableAttestation.from(spec, attestation))
            .collect(Collectors.toList()));
    attestationList.addAll(
        fork1Block2.getBody().getAttestations().stream()
            .map(attestation -> ValidateableAttestation.from(spec, attestation))
            .collect(Collectors.toList()));
    assertThat(argument.getAllValues()).containsExactlyInAnyOrderElementsOf(attestationList);

    verify(proposerSlashingOperationPool).removeAll(fork2Block1.getBody().getProposerSlashings());
    verify(attesterSlashingOperationPool).removeAll(fork2Block1.getBody().getAttesterSlashings());
    verify(exitOperationPool).removeAll(fork2Block1.getBody().getVoluntaryExits());
    verify(attestationPool)
        .onAttestationsIncludedInBlock(
            fork2Block1.getSlot(), fork2Block1.getBody().getAttestations());

    verify(proposerSlashingOperationPool).removeAll(fork2Block2.getBody().getProposerSlashings());
    verify(attesterSlashingOperationPool).removeAll(fork2Block2.getBody().getAttesterSlashings());
    verify(exitOperationPool).removeAll(fork2Block2.getBody().getVoluntaryExits());
    verify(attestationPool)
        .onAttestationsIncludedInBlock(
            fork2Block2.getSlot(), fork2Block2.getBody().getAttestations());
  }

  @Test
  void shouldOnlyRemoveOperations() {
    BeaconBlock block1 = dataStructureUtil.randomBeaconBlock(10);
    BeaconBlock block2 = dataStructureUtil.randomBeaconBlock(11);

    UInt64 commonAncestorSlot = UInt64.valueOf(9);

    NavigableMap<UInt64, Bytes32> nowCanonicalBlockRoots = new TreeMap<>();
    nowCanonicalBlockRoots.put(UInt64.valueOf(12), block1.hashTreeRoot());
    nowCanonicalBlockRoots.put(UInt64.valueOf(13), block2.hashTreeRoot());
    when(recentChainData.getAncestorsOnFork(commonAncestorSlot, block2.hashTreeRoot()))
        .thenReturn(nowCanonicalBlockRoots);

    // reOrged old chain
    when(recentChainData.getAncestorsOnFork(commonAncestorSlot, Bytes32.ZERO))
        .thenReturn(new TreeMap<>());

    when(recentChainData.retrieveBlockByRoot(block1.hashTreeRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block1)));
    when(recentChainData.retrieveBlockByRoot(block2.hashTreeRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block2)));

    when(attestationManager.onAttestation(any()))
        .thenReturn(SafeFuture.completedFuture(AttestationProcessingResult.SUCCESSFUL));

    operationsReOrgManager.chainHeadUpdated(
        UInt64.valueOf(13),
        block2.getStateRoot(),
        block2.hashTreeRoot(),
        false,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        ReorgContext.of(Bytes32.ZERO, UInt64.ZERO, Bytes32.ZERO, commonAncestorSlot, Bytes32.ZERO));

    verify(recentChainData).getAncestorsOnFork(commonAncestorSlot, block2.hashTreeRoot());

    verify(attestationPool, never()).onReorg(commonAncestorSlot);
    verify(exitOperationPool, never()).addAll(any());
    verify(proposerSlashingOperationPool, never()).addAll(any());
    verify(attesterSlashingOperationPool, never()).addAll(any());
    verify(attestationManager, never()).onAttestation(any());

    verify(proposerSlashingOperationPool).removeAll(block2.getBody().getProposerSlashings());
    verify(attesterSlashingOperationPool).removeAll(block2.getBody().getAttesterSlashings());
    verify(exitOperationPool).removeAll(block2.getBody().getVoluntaryExits());
    verify(attestationPool)
        .onAttestationsIncludedInBlock(block2.getSlot(), block2.getBody().getAttestations());

    verify(proposerSlashingOperationPool).removeAll(block1.getBody().getProposerSlashings());
    verify(attesterSlashingOperationPool).removeAll(block1.getBody().getAttesterSlashings());
    verify(exitOperationPool).removeAll(block1.getBody().getVoluntaryExits());
    verify(attestationPool)
        .onAttestationsIncludedInBlock(block1.getSlot(), block1.getBody().getAttestations());
  }
}
