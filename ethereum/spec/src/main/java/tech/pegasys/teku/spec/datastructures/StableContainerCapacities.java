/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures;

public class StableContainerCapacities {
  public static final int MAX_BEACON_STATE_FIELDS = 128;
  public static final int MAX_BEACON_BLOCK_BODY_FIELDS = 64;
  public static final int MAX_EXECUTION_PAYLOAD_FIELDS = 64;
  public static final int MAX_INDEXED_ATTESTATION_FIELDS = 8;
  public static final int MAX_ATTESTATION_FIELDS = 8;
}
