/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.encodings;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcException;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public interface RpcEncoding {
  <T extends SimpleOffsetSerializable> Bytes encodeMessage(T data);

  <T> T decodeMessage(Bytes message, Class<T> clazz) throws RpcException;

  Bytes encodeError(String errorMessage);

  String decodeError(Bytes message) throws RpcException;

  String getName();
}
