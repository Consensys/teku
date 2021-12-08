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

package tech.pegasys.teku.util.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class ConstantsReader {
  private static final ImmutableList<String> PRESETS =
      ImmutableList.of("mainnet", "minimal", "swift");
  private static final String PRESET_PATH = "presets/";
  private static final String CONFIG_PATH = "configs/";
  private static final String PRESET_FIELD = "PRESET_BASE";
  private static final ImmutableList<String> FIELDS_TO_IGNORE =
      ImmutableList.of(
          PRESET_FIELD,
          "CONFIG_NAME", // Legacy field
          // Altair fields
          "ALTAIR_FORK_VERSION",
          "ALTAIR_FORK_EPOCH",
          "INACTIVITY_SCORE_BIAS",
          "INACTIVITY_SCORE_RECOVERY_RATE",
          // Unsupported, upcoming fork-related keys
          "MERGE_FORK_VERSION",
          "MERGE_FORK_EPOCH",
          "SHARDING_FORK_VERSION",
          "SHARDING_FORK_EPOCH",
          "TRANSITION_TOTAL_DIFFICULTY", // has been renamed to TERMINAL_TOTAL_DIFFICULTY
          "MIN_ANCHOR_POW_BLOCK_DIFFICULTY",
          "TERMINAL_TOTAL_DIFFICULTY",
          "TERMINAL_BLOCK_HASH",
          "TERMINAL_BLOCK_HASH_ACTIVATION_EPOCH",
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
          // Removed from Constants (only available through SpecConfig)
          "GENESIS_FORK_VERSION",
          "BASE_REWARDS_PER_EPOCH",
          "DEPOSIT_CONTRACT_TREE_DEPTH",
          "JUSTIFICATION_BITS_LENGTH",
          "BLS_WITHDRAWAL_PREFIX",
          "TARGET_AGGREGATORS_PER_COMMITTEE",
          "RANDOM_SUBNETS_PER_VALIDATOR",
          "EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION",
          "BASE_REWARD_FACTOR",
          "INACTIVITY_PENALTY_QUOTIENT",
          "MAX_PROPOSER_SLASHINGS",
          "MAX_ATTESTER_SLASHINGS",
          "MAX_ATTESTATIONS",
          "MAX_DEPOSITS",
          "MAX_VOLUNTARY_EXITS",
          "SAFE_SLOTS_TO_UPDATE_JUSTIFIED",
          "DEPOSIT_NETWORK_ID",
          "DEPOSIT_CONTRACT_ADDRESS",
          "PROPOSER_SCORE_BOOST");

  private static final ImmutableMap<Class<?>, Function<Object, ?>> PARSERS =
      ImmutableMap.<Class<?>, Function<Object, ?>>builder()
          .put(Integer.TYPE, ConstantsReader::parseInt)
          .put(Long.TYPE, toString(Long::valueOf))
          .put(UInt64.class, toString(UInt64::valueOf))
          .put(String.class, Function.identity())
          .put(Bytes.class, ConstantsReader::bytesParser)
          .put(boolean.class, toString(Boolean::valueOf))
          .build();

  private static Bytes bytesParser(final Object value) {
    if (value instanceof BigInteger) {
      BigInteger bigInteger = (BigInteger) value;
      final Bytes bytes = Bytes.of(bigInteger.toByteArray());
      return bytes.trimLeadingZeros();
    } else if (value instanceof Long) {
      return Bytes.ofUnsignedLong((long) value);
    } else if (value instanceof Integer) {
      return Bytes.ofUnsignedInt((int) value);
    }
    throw new IllegalArgumentException("Could not determine type of " + value);
  }

  public static void loadConstantsFrom(final String source) {
    try (final InputStream input = createConfigInputStream(source)) {
      final Optional<String> maybePreset = loadConstants(input);
      if (maybePreset.isEmpty()) {
        // Legacy config files do not have a preset
        return;
      }
      try (final InputStream preset = createPresetInputStream(source, maybePreset.get())) {
        loadConstants(preset);
      }
    } catch (IOException | IllegalArgumentException e) {
      throw new InvalidConfigurationException("Failed to load constants from " + source, e);
    }
  }

  @VisibleForTesting
  @SuppressWarnings("unchecked")
  static Optional<String> loadConstants(final InputStream input) throws IOException {
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    final Map<String, Object> values =
        (Map<String, Object>)
            mapper
                .readerFor(
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class))
                .readValues(input)
                .next();
    values.forEach(ConstantsReader::setField);
    return Optional.ofNullable(values.get(PRESET_FIELD)).map(ConstantsReader::castPresetField);
  }

  private static String castPresetField(final Object value) {
    if (!(value instanceof String)) {
      throw new IllegalArgumentException(
          String.format(
              "Unable to parse %s field (value = '%s') as a string", PRESET_FIELD, value));
    }
    return (String) value;
  }

  private static InputStream createConfigInputStream(final String source) throws IOException {
    return ResourceLoader.classpathUrlOrFile(
            Constants.class,
            enumerateNetworkResources(),
            s -> s.endsWith(".yaml") || s.endsWith(".yml"))
        .load(CONFIG_PATH + source + ".yaml", source)
        .orElseThrow(() -> new FileNotFoundException("Could not load constants from " + source));
  }

  private static InputStream createPresetInputStream(final String source, final String preset)
      throws IOException {
    return ResourceLoader.classpathUrlOrFile(
            Constants.class,
            enumerateAvailablePresetResources(),
            s -> s.endsWith(".yaml") || s.endsWith(".yml"))
        .load(PRESET_PATH + preset + "/phase0.yaml", preset)
        .orElseThrow(
            () ->
                new FileNotFoundException(
                    String.format(
                        "Could not load preset '%s' for config source '%s'", preset, source)));
  }

  private static void setField(final String key, final Object value) {
    if (FIELDS_TO_IGNORE.contains(key)) {
      return;
    }

    try {
      final Field field = Constants.class.getField(key);
      if (!Modifier.isStatic(field.getModifiers())) {
        throw new IllegalArgumentException("Unknown constant: " + key);
      }
      field.set(null, parseValue(field, value));
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalArgumentException("Unknown constant: " + key, e);
    } catch (Throwable t) {
      throw new IllegalArgumentException("Unable to set constant: " + key, t);
    }
  }

  private static Object parseValue(final Field field, final Object value) {
    final Function<Object, ?> parser = PARSERS.get(field.getType());
    if (parser == null) {
      throw new IllegalArgumentException("Unknown constant type: " + field.getType());
    }
    try {
      return parser.apply(value);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Failed to parse value '" + value + "' for constant '" + field.getName() + "'");
    }
  }

  private static Integer parseInt(final Object input) {
    if (input instanceof Integer) {
      return (Integer) input;
    }
    final String value = input.toString();
    if (value.startsWith("0x")) {
      if (value.length() != 10) {
        throw new IllegalArgumentException("Little-endian constant is not four bytes: " + value);
      }
      return Integer.reverseBytes(Integer.decode(value));
    } else {
      return Integer.valueOf(value);
    }
  }

  private static <T> Function<Object, T> toString(final Function<String, T> function) {
    return value -> function.apply(value.toString());
  }

  private static List<String> enumerateNetworkResources() {
    return Constants.NETWORK_DEFINITIONS.stream()
        .map(s -> CONFIG_PATH + s + ".yaml")
        .collect(Collectors.toList());
  }

  private static List<String> enumerateAvailablePresetResources() {
    return PRESETS.stream()
        .map(s -> List.of(PRESET_PATH + s + "/phase0.yaml", PRESET_PATH + s + "/altair.yaml"))
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }
}
