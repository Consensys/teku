/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.sync;

import static com.google.common.base.Preconditions.checkNotNull;

public class SyncConfig {
  private final boolean isEnabled;
  private final boolean isMultiPeerSyncEnabled;

  private SyncConfig(final boolean isEnabled, final boolean isMultiPeerSyncEnabled) {
    this.isEnabled = isEnabled;
    this.isMultiPeerSyncEnabled = isMultiPeerSyncEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isSyncEnabled() {
    return isEnabled;
  }

  public boolean isMultiPeerSyncEnabled() {
    return isMultiPeerSyncEnabled;
  }

  public static class Builder {
    private Boolean isEnabled = true;
    private Boolean isMultiPeerSyncEnabled = false;

    private Builder() {}

    public SyncConfig build() {
      return new SyncConfig(isEnabled, isMultiPeerSyncEnabled);
    }

    public Builder isSyncEnabled(final Boolean enabled) {
      checkNotNull(enabled);
      isEnabled = enabled;
      return this;
    }

    public Builder isMultiPeerSyncEnabled(final Boolean multiPeerSyncEnabled) {
      checkNotNull(multiPeerSyncEnabled);
      isMultiPeerSyncEnabled = multiPeerSyncEnabled;
      return this;
    }
  }
}
