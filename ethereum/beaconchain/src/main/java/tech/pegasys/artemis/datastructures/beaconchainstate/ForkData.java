/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.beaconchainstate;

import tech.pegasys.artemis.util.uint.UInt64;

public class ForkData {

  private UInt64 pre_fork_version;
  private UInt64 post_fork_version;
  private UInt64 fork_slot;

  public ForkData(UInt64 pre_fork_version, UInt64 post_fork_version, UInt64 fork_slot) {
    this.pre_fork_version = pre_fork_version;
    this.post_fork_version = post_fork_version;
    this.fork_slot = fork_slot;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UInt64 getPre_fork_version() {
    return pre_fork_version;
  }

  public void setPre_fork_version(UInt64 pre_fork_version) {
    this.pre_fork_version = pre_fork_version;
  }

  public UInt64 getPost_fork_version() {
    return post_fork_version;
  }

  public void setPost_fork_version(UInt64 post_fork_version) {
    this.post_fork_version = post_fork_version;
  }

  public UInt64 getFork_slot() {
    return fork_slot;
  }

  public void setFork_slot(UInt64 fork_slot) {
    this.fork_slot = fork_slot;
  }
}
