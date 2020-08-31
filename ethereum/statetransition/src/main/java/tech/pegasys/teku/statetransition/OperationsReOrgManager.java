package tech.pegasys.teku.statetransition;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

import java.util.NavigableMap;

public class OperationsReOrgManager implements ReorgEventChannel {
  final OperationPool<SignedVoluntaryExit> exitPool;
  final OperationPool<ProposerSlashing> proposerSlashingPool;
  final OperationPool<AttesterSlashing> attesterSlashingPool;
  final AttestationManager attestationManager;
  final AggregatingAttestationPool attestationPool;
  final RecentChainData recentChainData;

  public OperationsReOrgManager(OperationPool<SignedVoluntaryExit> exitPool,
                                OperationPool<ProposerSlashing> proposerSlashingPool,
                                OperationPool<AttesterSlashing> attesterSlashingPool,
                                AttestationManager attestationManager,
                                AggregatingAttestationPool attestationPool,
                                RecentChainData recentChainData) {
    this.exitPool = exitPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.attestationManager = attestationManager;
    this.attestationPool = attestationPool;
    this.recentChainData = recentChainData;
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void reorgOccurred(Bytes32 bestBlockRoot, UInt64 bestSlot, Bytes32 oldBestBlockRoot, UInt64 commonAncestorSlot) {
    NavigableMap<UInt64, Bytes32> notCanonicalBlockRoots = recentChainData.getAncestorRootsForRoot(commonAncestorSlot, oldBestBlockRoot);
    notCanonicalBlockRoots.values().stream()
            .map(recentChainData::retrieveBlockByRoot)
            .forEach(future -> future.thenAccept(optionalBlock -> optionalBlock.ifPresent(block -> {
                  SSZList<ProposerSlashing> proposerSlashings = block.getBody().getProposer_slashings();
                  SSZList<AttesterSlashing> attesterSlashings = block.getBody().getAttester_slashings();
                  SSZList<Attestation> attestations = block.getBody().getAttestations();
                  SSZList<SignedVoluntaryExit> exits = block.getBody().getVoluntary_exits();

                  proposerSlashings.forEach(proposerSlashingPool::add);
                  attesterSlashings.forEach(attesterSlashingPool::add);
                  exits.forEach(exitPool::add);
                  attestations.forEach(attestationManager::onAttestation);
                })));

    NavigableMap<UInt64, Bytes32> nowCanonicalBlockRoots = recentChainData.getAncestorRootsForRoot(commonAncestorSlot, bestBlockRoot);
    nowCanonicalBlockRoots.values().stream()
            .map(recentChainData::retrieveBlockByRoot)
            .forEach(future -> future.thenAccept(optionalBlock -> optionalBlock.ifPresent(block -> {
              SSZList<ProposerSlashing> proposerSlashings = block.getBody().getProposer_slashings();
              SSZList<AttesterSlashing> attesterSlashings = block.getBody().getAttester_slashings();
              SSZList<Attestation> attestations = block.getBody().getAttestations();
              SSZList<SignedVoluntaryExit> exits = block.getBody().getVoluntary_exits();

              proposerSlashingPool.removeAll(proposerSlashings);
              attesterSlashingPool.removeAll(attesterSlashings);
              exitPool.removeAll(exits);
              attestationPool.removeAll(attestations);
            })));
  }
}
