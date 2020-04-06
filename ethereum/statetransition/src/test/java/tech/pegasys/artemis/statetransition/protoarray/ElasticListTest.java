package tech.pegasys.artemis.statetransition.protoarray;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ElasticListTest {

  @Test
  void listGrowsToMatchRequest() {
    ElasticList<VoteTracker> list = new ElasticList<>(VoteTracker.DEFAULT);
    VoteTracker voteTracker = list.get(3);
    assertThat(list).hasSize(4);
    assertThat(voteTracker).isEqualTo(VoteTracker.DEFAULT);
  }
}
