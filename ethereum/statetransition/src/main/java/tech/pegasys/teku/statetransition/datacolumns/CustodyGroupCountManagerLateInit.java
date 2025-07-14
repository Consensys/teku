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

public class CustodyGroupCountManagerLateInit implements CustodyGroupCountManager {
  private volatile CustodyGroupCountManager custodyGroupCountManager;

  public void setCustodyGroupCountManager(final CustodyGroupCountManager custodyGroupCountManager) {
    this.custodyGroupCountManager = custodyGroupCountManager;
  }

  @Override
  public int getCustodyGroupCount() {
    checkInitialized();
    return custodyGroupCountManager.getCustodyGroupCount();
  }

  @Override
  public List<UInt64> getCustodyColumnIndices() {
    checkInitialized();
    return custodyGroupCountManager.getCustodyColumnIndices();
  }

  @Override
  public int getSampleGroupCount() {
    checkInitialized();
    return custodyGroupCountManager.getSampleGroupCount();
  }

  @Override
  public List<UInt64> getSamplingColumnIndices() {
    checkInitialized();
    return custodyGroupCountManager.getSamplingColumnIndices();
  }

  @Override
  public int getCustodyGroupSyncedCount() {
    checkInitialized();
    return custodyGroupCountManager.getCustodyGroupSyncedCount();
  }

  @Override
  public void setCustodyGroupSyncedCount(final int custodyGroupSyncedCount) {
    checkInitialized();
    custodyGroupCountManager.setCustodyGroupSyncedCount(custodyGroupSyncedCount);
  }

  private void checkInitialized() {
    if (custodyGroupCountManager == null) {
      throw new IllegalStateException(
          "CustodyGroupCountManagerLateInit has not been initialized yet");
    }
  }
}
