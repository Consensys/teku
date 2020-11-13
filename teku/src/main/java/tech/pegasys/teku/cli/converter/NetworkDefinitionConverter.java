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

package tech.pegasys.teku.cli.converter;

import static com.google.common.base.Preconditions.checkNotNull;

import picocli.CommandLine;
import tech.pegasys.teku.util.config.NetworkDefinition;

public class NetworkDefinitionConverter implements CommandLine.ITypeConverter<NetworkDefinition> {
  public static final String CHECKPOINT_ERROR =
      "Checkpoint arguments should be formatted as <BLOCK_ROOT>:<EPOCH_NUMBER> where blockRoot is a hex-encoded 32 byte value and epochNumber is a number in decimal format";

  @Override
  public NetworkDefinition convert(final String networkString) {
    try {
      checkNotNull(networkString);
      return NetworkDefinition.fromCliArg(networkString);
    } catch (Throwable e) {
      throw new CommandLine.TypeConversionException(CHECKPOINT_ERROR);
    }
  }
}
