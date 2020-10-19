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

package tech.pegasys.teku.networking.p2p.rpc;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface RpcStream {

  SafeFuture<Void> writeBytes(Bytes bytes) throws StreamClosedException;

  /**
   * Close the stream altogether, allowing no further reads or writes.
   *
   * @return A future completing when the stream is closed.
   */
  SafeFuture<Void> closeAbruptly();

  /**
   * Close the write side of the stream. When both sides of the stream close their write stream, the
   * entire stream will be closed.
   *
   * @return A future completing when the write stream is closed.
   */
  SafeFuture<Void> closeWriteStream();
}
