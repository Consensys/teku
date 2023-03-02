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

package tech.pegasys.teku.spec.config;

import static tech.pegasys.teku.spec.config.SpecConfigFormatter.camelToSnakeCase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.builder.AltairBuilder;
import tech.pegasys.teku.spec.config.builder.BellatrixBuilder;
import tech.pegasys.teku.spec.config.builder.CapellaBuilder;
import tech.pegasys.teku.spec.config.builder.DenebBuilder;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;

public class SpecConfigReader {
  private static final Logger LOG = LogManager.getLogger();
  public static final String PRESET_KEY = "PRESET_BASE";
  private static final String CONFIG_NAME_KEY = "CONFIG_NAME";
  private static final ImmutableSet<String> KEYS_TO_IGNORE =
      ImmutableSet.of(
          PRESET_KEY,
          CONFIG_NAME_KEY,
          // Old merge config item which is no longer used, ignore for backwards compatibility
          "TRANSITION_TOTAL_DIFFICULTY");
  private static final ImmutableSet<String> CONSTANT_KEYS =
      ImmutableSet.of(
          // Phase0 constants which may exist in legacy config files, but should now be ignored
          "BLS_WITHDRAWAL_PREFIX",
          "TARGET_AGGREGATORS_PER_COMMITTEE",
          "RANDOM_SUBNETS_PER_VALIDATOR",
          "EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION",
          "DOMAIN_BEACON_PROPOSER",
          "DOMAIN_BEACON_ATTESTER",
          "DOMAIN_RANDAO",
          "DOMAIN_DEPOSIT",
          "DOMAIN_VOLUNTARY_EXIT",
          "DOMAIN_SELECTION_PROOF",
          "DOMAIN_AGGREGATE_AND_PROOF",
          // Altair constants
          "DOMAIN_SYNC_COMMITTEE",
          "DOMAIN_CONTRIBUTION_AND_PROOF",
          "TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE",
          "DOMAIN_SYNC_COMMITTEE_SELECTION_PROOF",
          "SYNC_COMMITTEE_SUBNET_COUNT");

  private final ImmutableMap<Class<?>, Function<Object, ?>> parsers =
      ImmutableMap.<Class<?>, Function<Object, ?>>builder()
          .put(Integer.TYPE, this::parseInt)
          .put(Integer.class, this::parseInt)
          .put(Long.TYPE, this::parseLong)
          .put(Long.class, this::parseLong)
          .put(UInt64.class, fromString(UInt64::valueOf))
          .put(UInt256.class, fromString(str -> UInt256.valueOf(new BigInteger(str))))
          .put(String.class, Function.identity())
          .put(Bytes.class, fromString(Bytes::fromHexString))
          .put(Bytes4.class, fromString(Bytes4::fromHexString))
          .put(Bytes32.class, fromString(Bytes32::fromHexStringStrict))
          .put(boolean.class, fromString(Boolean::valueOf))
          .put(Eth1Address.class, fromString(Eth1Address::fromHexString))
          .build();

  final SpecConfigBuilder configBuilder = SpecConfig.builder();

  public SpecConfig build() {
    return configBuilder.build();
  }

  public SpecConfig build(Consumer<SpecConfigBuilder> modifier) {
    modifier.accept(configBuilder);
    return build();
  }

  /**
   * Reads and processes the resource, returns any referenced "preset" to be processed if a preset
   * field is set
   *
   * @param source The source to read
   * @throws IOException Thrown if an error occurs reading the source
   */
  public void readAndApply(final InputStream source, final boolean ignoreUnknownConfigItems)
      throws IOException {
    final Map<String, String> rawValues = readValues(source);
    loadFromMap(rawValues, ignoreUnknownConfigItems);
  }

  public void loadFromMap(
      final Map<String, String> rawValues, final boolean ignoreUnknownConfigItems) {
    final Map<String, String> unprocessedConfig = new HashMap<>(rawValues);
    final Map<String, String> apiSpecConfig = new HashMap<>(rawValues);
    // Remove any keys that we're ignoring
    KEYS_TO_IGNORE.forEach(
        key -> {
          unprocessedConfig.remove(key);
          if (!key.equals(PRESET_KEY) && !key.equals(CONFIG_NAME_KEY)) {
            apiSpecConfig.remove(key);
          }
        });

    // Process phase0 config
    configBuilder.rawConfig(apiSpecConfig);
    streamConfigSetters(configBuilder.getClass())
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(
                  setter, BuilderSupplier.fromBuilder(configBuilder), constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process altair config
    streamConfigSetters(AltairBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::altairBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process bellatrix config
    streamConfigSetters(BellatrixBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::bellatrixBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process capella config
    streamConfigSetters(CapellaBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::capellaBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process Deneb config
    streamConfigSetters(DenebBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::denebBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Check any constants that have been configured and then ignore
    final Set<String> configuredConstants =
        Sets.intersection(CONSTANT_KEYS, unprocessedConfig.keySet());
    if (configuredConstants.size() > 0) {
      LOG.info(
          "Ignoring non-configurable constants supplied in network configuration: {}",
          String.join(", ", configuredConstants));
    }
    CONSTANT_KEYS.forEach(unprocessedConfig::remove);

    if (unprocessedConfig.size() > 0) {
      final String unknownKeys = String.join(",", unprocessedConfig.keySet());
      if (!ignoreUnknownConfigItems) {
        throw new IllegalArgumentException("Detected unknown spec config entries: " + unknownKeys);
      } else {
        LOG.info("Ignoring unknown items in network configuration: {}", unknownKeys);
      }
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> readValues(final InputStream source) throws IOException {
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    try {
      return (Map<String, String>)
          mapper
              .readerFor(
                  mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class))
              .readValues(source)
              .next();
    } catch (NoSuchElementException e) {
      throw new IllegalArgumentException("Supplied spec config is empty");
    } catch (RuntimeJsonMappingException e) {
      throw new IllegalArgumentException("Cannot read spec config: " + e.getMessage());
    }
  }

  private Stream<Method> streamConfigSetters(Class<?> builderClass) {
    // Ignore any setters that aren't for individual config entries
    final Set<String> ignoredSetters = Set.of("rawConfig");

    return Arrays.stream(builderClass.getMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()))
        .filter(m -> m.getReturnType() == builderClass)
        .filter(m -> m.getParameterTypes().length == 1)
        .filter(m -> !ignoredSetters.contains(m.getName()));
  }

  private <T> void invokeSetter(
      final Method setterMethod,
      final BuilderSupplier<T> builder,
      final String constantKey,
      final Object rawValue) {
    if (rawValue == null) {
      return;
    }

    final Class<?> valueType = setterMethod.getParameterTypes()[0];
    final Object value = parseValue(valueType, constantKey, rawValue);
    builder.updateBuilder(
        b -> {
          try {
            setterMethod.invoke(b, value);
          } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private Object parseValue(final Class<?> valueType, final String key, final Object value) {
    final Function<Object, ?> parser = parsers.get(valueType);
    if (parser == null) {
      throw new IllegalStateException("Missing parser for constant type: " + valueType);
    }
    try {
      return parser.apply(value);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Failed to parse value for constant " + key + ": '" + value + "'", e);
    }
  }

  private int parseInt(final Object input) {
    final String stringValue = input.toString();
    final int value;
    if (input instanceof Integer) {
      value = (Integer) input;
    } else if (stringValue.startsWith("0x")) {
      value =
          Bytes.fromHexString(stringValue)
              .toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN)
              .intValueExact();
    } else {
      value = Integer.parseInt(stringValue, 10);
    }

    // Validate
    if (value < 0) {
      throw new IllegalArgumentException("Integer values must be positive");
    }

    return value;
  }

  private Long parseLong(final Object rawValue) {
    final long value = Long.valueOf(rawValue.toString(), 10);
    if (value < 0) {
      throw new IllegalArgumentException("Long values must be positive");
    }

    return value;
  }

  private <T> Function<Object, T> fromString(final Function<String, T> function) {
    return value -> function.apply(value.toString());
  }

  private interface BuilderSupplier<TBuilder> {
    static <T> BuilderSupplier<T> fromBuilder(T builder) {
      return (consumer) -> consumer.accept(builder);
    }

    void updateBuilder(final Consumer<TBuilder> consumer);
  }
}
