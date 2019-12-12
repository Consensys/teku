package tech.pegasys.artemis.storage;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.Store.Transaction;

class DatabaseTest {
  private final EventBus eventBus = mock(EventBus.class);
  private final Database database = Database.createInMemory(eventBus);
  private int seed = 49824;

  @Test
  public void getFinalizedRootAtSlot_noBlocksStored() {
    assertThat(database.getFinalizedRootAtSlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getFinalizedRootAtSlot_slotAfterFinalizedSlot() {
    final UnsignedLong finalizedEpoch = ONE;
    final UnsignedLong firstNonFinalizedSlot = firstNonFinalizedSlot(finalizedEpoch);
    final UnsignedLong lastFinalizedSlot = lastFinalizedSlot(finalizedEpoch);
    final Bytes32 blockRoot = insert(finalizedEpoch, lastFinalizedSlot.longValue());

    assertThat(database.getFinalizedRootAtSlot(lastFinalizedSlot)).contains(blockRoot);
    assertThat(database.getFinalizedRootAtSlot(firstNonFinalizedSlot)).isEmpty();
  }

  @Test
  public void getFinalizedRootAtSlot_blockPresentInRequestedSlot() {
    final UnsignedLong blockSlot = UnsignedLong.valueOf(3);
    final Bytes32 blockRoot = insert(ONE, blockSlot.longValue());
    assertThat(database.getFinalizedRootAtSlot(blockSlot)).contains(blockRoot);
  }

  @Test
  public void getFinalizedRootAtSlot_lastBlockPriorToRequestedSlot() {
    final Bytes32 blockRoot = insert(ONE, 3);
    assertThat(database.getFinalizedRootAtSlot(UnsignedLong.valueOf(4))).contains(blockRoot);
  }

  @Test
  public void getFinalizedRootAtSlot_slotBetweenTwoFinalizedBlocks() {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(10);
    final Bytes32 blockRoot1 = insert(finalizedEpoch, 3);
    final Bytes32 blockRoot2 = insert(finalizedEpoch, 5);

    assertThat(database.getFinalizedRootAtSlot(UnsignedLong.valueOf(3))).contains(blockRoot1);
    assertThat(database.getFinalizedRootAtSlot(UnsignedLong.valueOf(4))).contains(blockRoot1);
    assertThat(database.getFinalizedRootAtSlot(UnsignedLong.valueOf(5))).contains(blockRoot2);
    assertThat(database.getFinalizedRootAtSlot(UnsignedLong.valueOf(6))).contains(blockRoot2);
  }

  private UnsignedLong firstNonFinalizedSlot(final UnsignedLong finalizedEpoch) {
    return compute_start_slot_at_epoch(finalizedEpoch.plus(ONE));
  }

  private UnsignedLong lastFinalizedSlot(final UnsignedLong finalizedEpoch) {
    return firstNonFinalizedSlot(finalizedEpoch).minus(ONE);
  }

  private Bytes32 insert(final UnsignedLong finalizedEpoch, final long blockSlot) {
    final Transaction transaction = mock(Transaction.class);
    when(transaction.getFinalizedCheckpoint())
        .thenReturn(new Checkpoint(finalizedEpoch, Bytes32.fromHexString("0x1234")));
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(blockSlot, seed++);
    final Bytes32 blockRoot = block.signing_root("signature");
    when(transaction.getBlocks()).thenReturn(Collections.singletonMap(blockRoot, block));
    database.insert(transaction);
    return blockRoot;
  }
}
