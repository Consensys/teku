/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.MessageSupplier;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFacade;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.logic.versions.phase0.SpecLogicPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPhase0;

public class TekuConfigurationPrinter {
  private static final String FAIL_MESSAGE = "Failed to print Teku configuration";

  /**
   * Tries to print provided configuration in human-readable form skipping {@link
   * tech.pegasys.teku.spec.Spec} and other duplicated inclusions. Print is very fragile and could
   * stumble on any unknown class, in this case supplier returns error message with exception.
   *
   * @param tekuConfiguration Configuration
   * @return lazy message supplier. If input was printed successfully, message contains readable
   *     configuration, otherwise it contains error message and thrown Exception
   */
  public static MessageSupplier lazyPrint(final TekuConfiguration tekuConfiguration) {
    return () -> {
      Pair<String, Optional<Exception>> result = printSafely(tekuConfiguration);
      if (result.getRight().isEmpty()) {
        return new ParameterizedMessage(result.getLeft());
      } else {
        return new ParameterizedMessage(result.getLeft(), result.getRight().get());
      }
    };
  }

  private static Pair<String, Optional<Exception>> printSafely(
      final TekuConfiguration tekuConfiguration) {
    try {
      return Pair.of(printConfiguration(tekuConfiguration), Optional.empty());
    } catch (Exception ex) {
      return Pair.of(FAIL_MESSAGE, Optional.of(ex));
    }
  }

  private static String printConfiguration(final TekuConfiguration tekuConfiguration) {
    return ReflectionToStringBuilder.toString(tekuConfiguration, new RecursiveToStringStyleEx());
  }

  public static class RecursiveToStringStyleEx extends RecursiveToStringStyle {
    private static final Set<Class<?>> excludeDeepRecursionClasses = new HashSet<>();

    static {
      excludeDeepRecursionClasses.addAll(
          Arrays.asList(
              Enum.class,
              Optional.class,
              OptionalInt.class,
              Duration.class,
              BeaconChainControllerFacade.class,
              SchemaDefinitionsPhase0.class,
              SpecLogicPhase0.class,
              SpecConfig.class,
              Spec.class,
              UInt64.class,
              Bytes4.class));
    }

    /**
     * Returns whether or not to recursively format the given {@code Class}. By default, this method
     * always returns {@code true}, but may be overwritten by sub-classes to filter specific
     * classes.
     *
     * @param clazz The class to test.
     * @return Whether or not to recursively format the given {@code Class}.
     */
    @Override
    protected boolean accept(final Class<?> clazz) {
      for (Class<?> excludeClass : excludeDeepRecursionClasses) {
        if (excludeClass.isAssignableFrom(clazz)) {
          return false;
        }
      }
      if (clazz.getCanonicalName() != null
          && clazz.getCanonicalName().startsWith("org.apache.tuweni")) {
        return false;
      }
      if (Objects.equals(clazz.getCanonicalName(), "sun.nio.fs.UnixPath")) {
        return false;
      }
      return true;
    }
  }
}
