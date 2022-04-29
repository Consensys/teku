/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface VoteTracker {

  VoteTracker DEFAULT = new VoteTrackerV1(Bytes32.ZERO, Bytes32.ZERO, UInt64.ZERO);

  static VoteTracker create(Bytes32 currentRoot, Bytes32 nextRoot, UInt64 nextEpoch) {
    return new VoteTrackerV1(currentRoot, nextRoot, nextEpoch);
  }

  static VoteTracker markToEquivocate(final VoteTracker voteTracker) {
    return new VoteTrackerV2(
        voteTracker.getCurrentRoot(),
        voteTracker.getNextRoot(),
        voteTracker.getNextEpoch(),
        true,
        false);
  }

  static VoteTracker createEquivocated(final VoteTracker voteTracker) {
    return new VoteTrackerV2(
        voteTracker.getCurrentRoot(),
        voteTracker.getNextRoot(),
        voteTracker.getNextEpoch(),
        false,
        true);
  }

  Bytes32 getCurrentRoot();

  Bytes32 getNextRoot();

  UInt64 getNextEpoch();

  default boolean isMarkedToEquivocate() {
    return false;
  }

  default boolean isEquivocated() {
    return false;
  }

  Version getVersion();

  enum Version {
    V1,
    V2
  }
}
