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

package tech.pegasys.artemis.api.schema;

import com.google.common.primitives.UnsignedLong;

public class ProposerSlashing {
  public final UnsignedLong proposer_index;
  public final SignedBeaconBlockHeader header_1;
  public final SignedBeaconBlockHeader header_2;

  public ProposerSlashing(
      tech.pegasys.artemis.datastructures.operations.ProposerSlashing proposerSlashing) {
    proposer_index = proposerSlashing.getProposer_index();
    header_1 = new SignedBeaconBlockHeader(proposerSlashing.getHeader_1());
    header_2 = new SignedBeaconBlockHeader(proposerSlashing.getHeader_2());
  }
}
