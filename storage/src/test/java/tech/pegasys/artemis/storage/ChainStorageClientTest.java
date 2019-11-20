package tech.pegasys.artemis.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.config.Constants;

class ChainStorageClientTest {

  private static final Bytes32 BEST_BLOCK_ROOT = Bytes32.fromHexString("0x8462485245");
  private static final BeaconBlock BLOCK1 = DataStructureUtil.randomBeaconBlock(0, 1);
  private static final BeaconBlock BLOCK2 = DataStructureUtil.randomBeaconBlock(0, 2);
  private final EventBus eventBus = mock(EventBus.class);
  private final Store store = mock(Store.class);
  private final ChainStorageClient storageClient = new ChainStorageClient(eventBus);
  private int seed = 428942;

  @BeforeAll
  public static void beforeAll() {
    Constants.setConstants("minimal");
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenStoreNotSet() {
    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenBestBlockNotSet() {
    storageClient.setStore(store);
    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnGenesisBlock() {
    storageClient.setStore(store);
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, UnsignedLong.ZERO);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++);
    final BeaconBlock genesisBlock = BLOCK1;
    final Bytes32 genesisBlockHash = genesisBlock.hash_tree_root();
    // At the start of the chain, the slot number is the index into historical roots
    bestState.getBlock_roots().set(0, genesisBlockHash);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(genesisBlockHash)).thenReturn(genesisBlock);

    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).contains(genesisBlock);
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenSlotBeforeHistoricalRootWindow() {
    storageClient.setStore(store);
    // First slot where the block roots start overwriting themselves and dropping history
    final UnsignedLong bestSlot = UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT);
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++);
    final BeaconBlock bestBlock = BLOCK1;
    final Bytes32 bestBlockHash = bestBlock.hash_tree_root();
    // Overwrite the genesis hash.
    bestState.getBlock_roots().set(0, bestBlockHash);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(bestBlockHash)).thenReturn(bestBlock);

    // Slot 0 has now been overwritten by our current best slot so we can't get that block anymore.
    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnCorrectBlockFromHistoricalWindow() {
    storageClient.setStore(store);
    // We've wrapped around a lot of times and are 5 slots into the next "loop"
    final int historicalIndex = 5;
    final UnsignedLong bestSlot =
        UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT)
            .times(UnsignedLong.valueOf(900000000))
            .plus(UnsignedLong.valueOf(historicalIndex));

    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++);
    final BeaconBlock bestBlock = BLOCK1;
    final Bytes32 bestBlockHash = bestBlock.hash_tree_root();
    // Overwrite the genesis hash.
    bestState.getBlock_roots().set(historicalIndex, bestBlockHash);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(bestBlockHash)).thenReturn(bestBlock);

    assertThat(storageClient.getBlockBySlot(bestSlot)).contains(bestBlock);
  }

  @Test
  public void isIncludedInBestState_falseWhenNoStoreSet() {
    assertThat(storageClient.isIncludedInBestState(BLOCK1)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenBestBlockNotSet() {
    storageClient.setStore(store);
    assertThat(storageClient.isIncludedInBestState(BLOCK1)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenNoBlockAtSlot() {
    storageClient.setStore(store);
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, UnsignedLong.ONE);

    // bestState has no historical block roots so definitely nothing canonical at the block's slot
    final BeaconState bestState = DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);

    assertThat(storageClient.isIncludedInBestState(BLOCK1)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenBlockAtSlotDoesNotMatch() {
    storageClient.setStore(store);
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, UnsignedLong.ONE);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++);
    bestState.getBlock_roots().set(BLOCK1.getSlot().intValue(), BLOCK2.hash_tree_root());
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);

    assertThat(storageClient.isIncludedInBestState(BLOCK1)).isFalse();
  }

  @Test
  public void isIncludedInBestState_trueWhenBlockAtSlotDoesMatch() {
    storageClient.setStore(store);
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, UnsignedLong.ONE);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++);
    bestState.getBlock_roots().set(BLOCK1.getSlot().intValue(), BLOCK1.hash_tree_root());
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);

    assertThat(storageClient.isIncludedInBestState(BLOCK1)).isTrue();
  }
}
