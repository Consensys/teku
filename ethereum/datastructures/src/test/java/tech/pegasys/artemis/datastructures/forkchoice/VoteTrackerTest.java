package tech.pegasys.artemis.datastructures.forkchoice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

class VoteTrackerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  void shouldRoundTripViaSsz() {
    VoteTracker voteTracker = dataStructureUtil.randomVoteTracker();
    Bytes ser = SimpleOffsetSerializer.serialize(voteTracker);
    VoteTracker deser = SimpleOffsetSerializer.deserialize(ser, VoteTracker.class);
    assertEquals(voteTracker, deser);
  }
}
