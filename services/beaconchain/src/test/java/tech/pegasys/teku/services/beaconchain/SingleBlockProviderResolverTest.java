package tech.pegasys.teku.services.beaconchain;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerBasic;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SingleBlockProviderResolverTest {


    private final DasSamplerBasic dasBasicSampler = Mockito.mock(DasSamplerBasic.class);
    private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool = Mockito.mock(BlockBlobSidecarsTrackersPool.class);
    private final Spec spec = TestSpecFactory.createMinimalFulu();
    private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    @Test
    void shouldFetchBlockFromDasBasicSampler() {

        SignedBeaconBlock expectedBlock = dataStructureUtil.randomSignedBeaconBlock();
        when(dasBasicSampler.getBlock(expectedBlock.getRoot())).thenReturn(Optional.of(expectedBlock));

        SingleBlockProviderResolver resolver = new SingleBlockProviderResolver(BlockBlobSidecarsTrackersPool.NOOP, Optional.of(dasBasicSampler));
        SignedBeaconBlock actualBlock = resolver.getBlock(expectedBlock.getRoot()).get();

        assertEquals(expectedBlock, actualBlock);
    }

    @Test
    void shouldFetchBlockFromBlockBlobSidecarsTrackersPool() {

        SignedBeaconBlock expectedBlock = dataStructureUtil.randomSignedBeaconBlock();
        when(dasBasicSampler.getBlock(expectedBlock.getRoot())).thenReturn(Optional.empty());
        when(blockBlobSidecarsTrackersPool.getBlock(expectedBlock.getRoot())).thenReturn(Optional.of(expectedBlock));

        SingleBlockProviderResolver resolver = new SingleBlockProviderResolver(blockBlobSidecarsTrackersPool, Optional.of(dasBasicSampler));
        SignedBeaconBlock actualBlock = resolver.getBlock(expectedBlock.getRoot()).get();

        verify(dasBasicSampler).getBlock(expectedBlock.getRoot());
        verify(blockBlobSidecarsTrackersPool).getBlock(expectedBlock.getRoot());
        assertEquals(expectedBlock, actualBlock);
    }

    @Test
    void shouldReturnEmptyIfBlockNotPresentInEitherSource() {

        SignedBeaconBlock expectedBlock = dataStructureUtil.randomSignedBeaconBlock();
        when(dasBasicSampler.getBlock(expectedBlock.getRoot())).thenReturn(Optional.empty());
        when(blockBlobSidecarsTrackersPool.getBlock(expectedBlock.getRoot())).thenReturn(Optional.empty());

        SingleBlockProviderResolver resolver = new SingleBlockProviderResolver(blockBlobSidecarsTrackersPool, Optional.of(dasBasicSampler));
        Optional<SignedBeaconBlock> actualBlock = resolver.getBlock(expectedBlock.getRoot());

        verify(dasBasicSampler).getBlock(expectedBlock.getRoot());
        verify(blockBlobSidecarsTrackersPool).getBlock(expectedBlock.getRoot());
        assertEquals(Optional.empty(), actualBlock);
    }




}