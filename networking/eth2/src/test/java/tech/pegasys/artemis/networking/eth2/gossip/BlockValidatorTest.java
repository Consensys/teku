package tech.pegasys.artemis.networking.eth2.gossip;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.BLSKeyGenerator;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.core.ChainBuilder;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.core.StateTransitionException;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.BlockValidationResult;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.BlockValidator;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.artemis.storage.client.RecentChainData;

import java.util.List;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

public class BlockValidatorTest {
  private final EventBus eventBus = new EventBus();

  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(eventBus);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(2, recentChainData);

  private BlockValidator blockValidator;

  @BeforeEach
  void setUp() {
    beaconChainUtil.initializeStorage();
    blockValidator = new BlockValidator(recentChainData, new StateTransition());
  }

  @Test
  void shouldReturnValidForValidBlock() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);

    BlockValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(BlockValidationResult.VALID);
  }

  @Test
  void shouldReturnSavedForFutureForValidBlock() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);

    BlockValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(BlockValidationResult.SAVED_FOR_FUTURE);
  }

  @Test
  void shouldReturnInvalidForBlockOlderThanFinalizedSlot() throws Exception {
    UnsignedLong finalizedEpoch = UnsignedLong.valueOf(10);
    UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(finalizedSlot.minus(ONE));
    beaconChainUtil.finalizeChainAtEpoch(finalizedEpoch);
    beaconChainUtil.setSlot(recentChainData.getBestSlot());

    BlockValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(BlockValidationResult.INVALID);
  }
}
