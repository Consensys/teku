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

package tech.pegasys.artemis.datastructures.BeaconChainOperations;

import tech.pegasys.artemis.util.uint.UInt384;

public class SlashableVoteData {

  private int[] aggregate_signature_poc_0_indices;
  private int[] aggregate_signature_poc_1_indices;
  private AttestationData data;
  private UInt384[] aggregate_signature;

  public SlashableVoteData() {

  }
}
