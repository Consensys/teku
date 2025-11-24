package tech.pegasys.teku.services.beaconchain;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.dataproviders.lookup.SingleBlockProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerBasic;

import java.util.Optional;

public class SingleBlockProviderResolver implements SingleBlockProvider {

    private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
    private final Optional<DasSamplerBasic> dasSamplerBasic;

    public SingleBlockProviderResolver(final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool, final Optional<DasSamplerBasic> dasSamplerBasic) {
        this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
        this.dasSamplerBasic = dasSamplerBasic;
    }


    @Override
    public Optional<SignedBeaconBlock> getBlock(final Bytes32 blockRoot) {
        return dasSamplerBasic.map(samplerBasic -> samplerBasic.getBlock(blockRoot)
                .or(() -> blockBlobSidecarsTrackersPool.getBlock(blockRoot)))
                .orElseGet(() -> blockBlobSidecarsTrackersPool.getBlock(blockRoot));
    }
}
