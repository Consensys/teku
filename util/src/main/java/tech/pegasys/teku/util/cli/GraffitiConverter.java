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

package tech.pegasys.teku.util.cli;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine;

public class GraffitiConverter implements CommandLine.ITypeConverter<Bytes32> {
  @Override
  public Bytes32 convert(final String value) {
    byte[] input = value.getBytes(StandardCharsets.UTF_8);
    if (input.length > 32) {
      throw (new CommandLine.TypeConversionException(
          "'"
              + value
              + "' converts to "
              + input.length
              + " bytes. A maximum of 32 bytes can be used as graffiti."));
    }

    byte[] bytes = new byte[32];
    System.arraycopy(input, 0, bytes, 0, input.length);
    return Bytes32.wrap(bytes);
  }
}
