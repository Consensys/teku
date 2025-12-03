package tech.pegasys.teku.statetransition.datacolumns;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.dataproviders.lookup.SingleBlockProvider;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DasSamplerBasic extends DataAvailabilitySampler, SlotEventsChannel {

    DasSamplerBasic NOOP = new DasSamplerBasic() {
        @Override
        public Map<Bytes32, DataColumnSamplingTracker> getRecentlySampledColumnsByRoot() {
            return Map.of();
        }

        @Override
        public void onNewValidatedDataColumnSidecar(final DataColumnSlotAndIdentifier columnId, final RemoteOrigin remoteOrigin) {

        }

        @Override
        public SafeFuture<List<UInt64>> checkDataAvailability(final SignedBeaconBlock beaconBlock) {
            return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SamplingEligibilityStatus checkSamplingEligibility(final BeaconBlock block) {
            return SamplingEligibilityStatus.NOT_REQUIRED_NO_BLOBS;
        }

        @Override
        public boolean containsBlock(final Bytes32 blockRoot) {
            return false;
        }

        @Override
        public Optional<SignedBeaconBlock> getBlock(Bytes32 blockRoot){
            return Optional.empty();
        }

        @Override
        public void onSlot(final UInt64 slot) {

        }

        @Override
        public void flush() {
        }
    };

    @VisibleForTesting
    Map<Bytes32, DataColumnSamplingTracker> getRecentlySampledColumnsByRoot();

    boolean containsBlock(Bytes32 blockRoot);

    Optional<SignedBeaconBlock> getBlock(Bytes32 blockRoot);
}
