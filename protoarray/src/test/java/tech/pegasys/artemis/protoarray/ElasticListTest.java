/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.protoarray;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;

public class ElasticListTest {

  @Test
  void listGrowsToMatchRequest() {
    ElasticList<VoteTracker> list = new ElasticList<>(VoteTracker::Default);
    VoteTracker voteTracker = list.get(3);
    assertThat(list).hasSize(4);
    assertThat(voteTracker).isEqualTo(VoteTracker.Default());
  }

  @Test
  void testDefaultOtherObjectStaySameWhenAnObjectChange() {
    ElasticList<VoteTracker> list = new ElasticList<>(VoteTracker::Default);
    VoteTracker voteTracker1 = list.get(3);
    voteTracker1.setNextEpoch(UnsignedLong.valueOf(3));
    VoteTracker voteTracker2 = list.get(2);
    assertThat(voteTracker1.getNextEpoch())
            .isEqualTo(UnsignedLong.valueOf(3));
    assertThat(voteTracker1.getNextEpoch())
            .isNotEqualByComparingTo(voteTracker2.getNextEpoch());
    assertThat(voteTracker2.getNextEpoch())
            .isEqualTo(VoteTracker.Default().getNextEpoch());
  }
}
