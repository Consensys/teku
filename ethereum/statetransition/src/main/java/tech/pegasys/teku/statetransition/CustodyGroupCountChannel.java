/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;

public interface CustodyGroupCountChannel extends VoidReturningChannelInterface {

  CustodyGroupCountChannel NOOP =
      new CustodyGroupCountChannel() {
        @Override
        public void onGroupCountUpdate(final int custodyGroupCount, final int samplingGroupCount) {}

        @Override
        public void onCustodyGroupCountSynced(final int groupCount) {}
      };

  static CustodyGroupCountChannel createCustodyGroupCountSyncedSubscriber(
      final Consumer<Integer> custodyGroupCountSyncedSubscriber) {
    return new CustodyGroupCountChannel() {
      @Override
      public void onGroupCountUpdate(final int custodyGroupCount, final int samplingGroupCount) {}

      @Override
      public void onCustodyGroupCountSynced(final int groupCount) {
        custodyGroupCountSyncedSubscriber.accept(groupCount);
      }
    };
  }

  void onGroupCountUpdate(int custodyGroupCount, int samplingGroupCount);

  void onCustodyGroupCountSynced(int groupCount);
}
