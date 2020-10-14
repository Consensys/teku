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

package tech.pegasys.teku.storage.server.metadata;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.util.serialization.JsonExplicit;

/**
 * Defines the configuration for a database. The configuration used when a database is created is
 * written to a metadata.yaml file and reloaded to ensure we continue using compatible values for
 * the lifetime of that database.
 *
 * <p>To preserve backwards compatibility always ensure that the value assigned in field
 * declarations is compatible with existing databases. These values will be used if the field didn't
 * exist at the time the database was created, so typically should match the default.
 *
 * <p>If the value to use for new databases, differs from the original, set it in a factory function
 *
 * <p>Values that are safe to change for existing databases are marked with {@link
 * Access#WRITE_ONLY}. They will not be written to the metadata file but if present, the value will
 * be loaded providing a simple way to experiment with different values without it being fixed at
 * database creation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonExplicit
@JsonInclude(Include.NON_NULL)
public class V6DatabaseMetadata {

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonExplicit
  public static class SingleDBMetadata {
    @JsonProperty("configuration")
    private RocksDbConfiguration configuration;

    public SingleDBMetadata() {}

    public SingleDBMetadata(RocksDbConfiguration configuration) {
      this.configuration = configuration;
    }

    public RocksDbConfiguration getConfiguration() {
      return configuration;
    }

    @Override
    public String toString() {
      return "SingleDBMetadata{" + "configuration=" + configuration + '}';
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonExplicit
  public static class SeparateDBMetadata {
    @JsonProperty("hotDbConfiguration")
    private RocksDbConfiguration hotDbConfiguration;

    @JsonProperty("archiveDbConfiguration")
    private RocksDbConfiguration archiveDbConfiguration;

    public SeparateDBMetadata() {}

    public SeparateDBMetadata(
        RocksDbConfiguration hotDbConfiguration, RocksDbConfiguration archiveDbConfiguration) {
      this.hotDbConfiguration = hotDbConfiguration;
      this.archiveDbConfiguration = archiveDbConfiguration;
    }

    public RocksDbConfiguration getHotDbConfiguration() {
      return hotDbConfiguration;
    }

    public RocksDbConfiguration getArchiveDbConfiguration() {
      return archiveDbConfiguration;
    }

    @Override
    public String toString() {
      return "SeparateDBMetadata{"
          + "hotDbConfiguration="
          + hotDbConfiguration
          + ", archiveDbConfiguration="
          + archiveDbConfiguration
          + '}';
    }
  }

  @JsonProperty("singleDb")
  private SingleDBMetadata singleDb;

  @JsonProperty("separateDb")
  private SeparateDBMetadata separateDb;

  public V6DatabaseMetadata() {}

  private V6DatabaseMetadata(RocksDbConfiguration singleDbConfiguration) {
    this.singleDb = new SingleDBMetadata(singleDbConfiguration);
  }

  private V6DatabaseMetadata(
      RocksDbConfiguration hotDbConfiguration, RocksDbConfiguration archiveDbConfiguration) {
    this.separateDb = new SeparateDBMetadata(hotDbConfiguration, archiveDbConfiguration);
  }

  public static V6DatabaseMetadata singleDBDefault() {
    return new V6DatabaseMetadata(RocksDbConfiguration.v6SingleDefaults());
  }

  public static V6DatabaseMetadata separateDBDefault() {
    return new V6DatabaseMetadata(
        RocksDbConfiguration.v5HotDefaults(), RocksDbConfiguration.v5ArchiveDefaults());
  }

  public Optional<SingleDBMetadata> getSingleDbConfiguration() {
    return Optional.ofNullable(singleDb);
  }

  public Optional<SeparateDBMetadata> getSeparateDbConfiguration() {
    return Optional.ofNullable(separateDb);
  }

  public boolean isSingleDB() {
    return getSingleDbConfiguration().isPresent();
  }

  public static V6DatabaseMetadata init(final File source, final V6DatabaseMetadata defaultValue)
      throws IOException {
    final ObjectMapper objectMapper =
        new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));
    if (source.exists()) {
      return objectMapper.readerFor(V6DatabaseMetadata.class).readValue(source);
    } else {
      objectMapper.writerFor(V6DatabaseMetadata.class).writeValue(source, defaultValue);
      return defaultValue;
    }
  }

  @Override
  public String toString() {
    return getSingleDbConfiguration()
        .map(Object::toString)
        .or(() -> getSeparateDbConfiguration().map(Object::toString))
        .orElseThrow();
  }
}
