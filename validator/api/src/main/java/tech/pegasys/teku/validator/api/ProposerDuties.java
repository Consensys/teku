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

package tech.pegasys.teku.validator.api;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;

public class ProposerDuties {
  private final Bytes32 currentTargetRoot;
  private final Bytes32 previousTargetRoot;
  private final List<ProposerDuty> proposerDuties;

  public ProposerDuties(
      final Bytes32 currentTargetRoot,
      final Bytes32 previousTargetRoot,
      final List<ProposerDuty> proposerDuties) {
    this.currentTargetRoot = currentTargetRoot;
    this.previousTargetRoot = previousTargetRoot;
    this.proposerDuties = proposerDuties;
  }

  public Bytes32 getCurrentTargetRoot() {
    return currentTargetRoot;
  }

  public Bytes32 getPreviousTargetRoot() {
    return previousTargetRoot;
  }

  public List<ProposerDuty> getProposerDuties() {
    return proposerDuties;
  }
}
