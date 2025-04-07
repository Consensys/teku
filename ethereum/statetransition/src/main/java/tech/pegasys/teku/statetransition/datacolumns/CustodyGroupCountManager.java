/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.datacolumns;

import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface CustodyGroupCountManager {
  CustodyGroupCountManager NOOP =
      new CustodyGroupCountManager() {
        @Override
        public int getCustodyGroupCount() {
          return 0;
        }

        @Override
        public List<UInt64> getCustodyColumnIndices() {
          return List.of();
        }

        @Override
        public int getCustodyGroupSyncedCount() {
          return 0;
        }

        @Override
        public void setCustodyGroupSyncedCount(int custodyGroupSyncedCount) {}
      };

  int getCustodyGroupCount();

  List<UInt64> getCustodyColumnIndices();

  int getCustodyGroupSyncedCount();

  void setCustodyGroupSyncedCount(int custodyGroupSyncedCount);
}
