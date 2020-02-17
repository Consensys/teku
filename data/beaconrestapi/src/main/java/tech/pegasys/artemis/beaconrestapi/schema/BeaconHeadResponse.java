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

package tech.pegasys.artemis.beaconrestapi.schema;

public class BeaconHeadResponse {
  public final long slot;
  public final String block_root;
  public final String state_root;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private long slot;
    private String block_root;
    private String state_root;

    private Builder() {}

    public Builder slot(long slot) {
      this.slot = slot;

      return this;
    }

    public Builder block_root(String block_root) {
      this.block_root = block_root;
      return this;
    }

    public Builder state_root(String state_root) {
      this.state_root = state_root;
      return this;
    }

    public BeaconHeadResponse build() {
      return new BeaconHeadResponse(slot, block_root, state_root);
    }
  }

  private BeaconHeadResponse(long slot, String block_root, String state_root) {
    this.slot = slot;
    this.block_root = block_root;
    this.state_root = state_root;
  }
}
