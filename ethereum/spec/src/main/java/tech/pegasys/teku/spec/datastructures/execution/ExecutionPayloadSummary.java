/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.execution;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface ExecutionPayloadSummary {

  UInt64 getGasLimit();

  UInt64 getGasUsed();

  UInt64 getBlockNumber();

  Bytes32 getBlockHash();

  UInt256 getBaseFeePerGas();

  UInt64 getTimestamp();

  Bytes32 getPrevRandao();

  Bytes32 getReceiptsRoot();

  Bytes32 getStateRoot();

  Bytes20 getFeeRecipient();

  Bytes32 getParentHash();

  Bytes getLogsBloom();

  Bytes getExtraData();

  Bytes32 getPayloadHash();

  boolean isDefaultPayload();

  default String toLogString() {
    return LogFormatter.formatBlock(getBlockNumber(), getBlockHash());
  }
}
