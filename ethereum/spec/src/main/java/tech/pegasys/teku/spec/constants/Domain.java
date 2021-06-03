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

package tech.pegasys.teku.spec.constants;

import tech.pegasys.teku.ssz.type.Bytes4;

public class Domain {
  // Phase0
  public static final Bytes4 BEACON_PROPOSER = Bytes4.fromHexString("0x00000000");
  public static final Bytes4 BEACON_ATTESTER = Bytes4.fromHexString("0x01000000");
  public static final Bytes4 RANDAO = Bytes4.fromHexString("0x02000000");
  public static final Bytes4 DEPOSIT = Bytes4.fromHexString("0x03000000");
  public static final Bytes4 VOLUNTARY_EXIT = Bytes4.fromHexString("0x04000000");
  public static final Bytes4 SELECTION_PROOF = Bytes4.fromHexString("0x05000000");
  public static final Bytes4 AGGREGATE_AND_PROOF = Bytes4.fromHexString("0x06000000");

  // Altair
  public static final Bytes4 SYNC_COMMITTEE = Bytes4.fromHexString("0x07000000");
  public static final Bytes4 SYNC_COMMITTEE_SELECTION_PROOF = Bytes4.fromHexString("0x08000000");
  public static final Bytes4 CONTRIBUTION_AND_PROOF = Bytes4.fromHexString("0x09000000");
}
