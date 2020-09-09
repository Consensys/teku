package tech.pegasys.teku.validator.coordinator;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZImmutableCollection;
import tech.pegasys.teku.storage.client.RecentChainData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

public class PerformanceTracker {

  private final Map<UInt64, SignedBeaconBlock> sentBlocksByEpoch = new HashMap<>();
  private final Map<UInt64, Attestation> sentAttestationsByEpoch = new HashMap<>();

  private final RecentChainData recentChainData;

  public PerformanceTracker(RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  private List<BeaconBlock> getBlocksInEpoch(UInt64 epoch) {
    UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);
    UInt64 nextEpochStartSlot = compute_start_slot_at_epoch(epoch.increment());

    List<Bytes32> blockRootsInEpoch = new ArrayList<>();
    for (UInt64 currSlot = epochStartSlot;
         currSlot.isLessThan(nextEpochStartSlot);
         currSlot = currSlot.increment()) {
      recentChainData.getBlockRootBySlot(currSlot).ifPresent(blockRootsInEpoch::add);
    }

    return blockRootsInEpoch.stream()
            .map(recentChainData::retrieveBlockByRoot)
            .map(SafeFuture::join)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
  }

  private List<Attestation> getAttestationsInEpoch(UInt64 epoch) {
    return getBlocksInEpoch(epoch).stream()
            .map(BeaconBlock::getBody)
            .map(BeaconBlockBody::getAttestations)
            .map(SSZImmutableCollection::asList)
            .collect(Collectors.toList());
  }

  public void saveSentAttestation(Attestation attestation) {
  }

  public void saveSentBlock(SignedBeaconBlock block) {

  }

  public void outputPerformanceInformation() {

  }
}
