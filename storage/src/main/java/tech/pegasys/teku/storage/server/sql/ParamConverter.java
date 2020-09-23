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

package tech.pegasys.teku.storage.server.sql;

import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ParamConverter {

  public static Object[] convertParams(final Object[] params) {
    return Stream.of(params).map(ParamConverter::convertParam).toArray();
  }

  private static Object convertParam(final Object param) {
    if (param instanceof Bytes) {
      return ((Bytes) param).toArrayUnsafe();
    } else if (param instanceof UInt64) {
      return ((UInt64) param).bigIntegerValue();
    } else if (param instanceof Enum<?>) {
      return ((Enum<?>) param).name();
    }
    return param;
  }
}
