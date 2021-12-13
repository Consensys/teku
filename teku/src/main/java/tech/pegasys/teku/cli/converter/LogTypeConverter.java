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

package tech.pegasys.teku.cli.converter;

import org.apache.logging.log4j.Level;
import picocli.CommandLine;
import picocli.CommandLine.ITypeConverter;

public class LogTypeConverter implements ITypeConverter<Level> {
  @Override
  public Level convert(String value) {
    switch (value.toUpperCase()) {
      case "OFF":
        return Level.OFF;
      case "FATAL":
        return Level.FATAL;
      case "ERROR":
        return Level.ERROR;
      case "WARN":
        return Level.WARN;
      case "INFO":
        return Level.INFO;
      case "DEBUG":
        return Level.DEBUG;
      case "TRACE":
        return Level.TRACE;
      case "ALL":
        return Level.ALL;
    }
    throw new CommandLine.TypeConversionException(
        "'"
            + value
            + "' is not a valid log level. Supported values are [OFF|FATAL|WARN|INFO|DEBUG|TRACE|ALL]");
  }
}
