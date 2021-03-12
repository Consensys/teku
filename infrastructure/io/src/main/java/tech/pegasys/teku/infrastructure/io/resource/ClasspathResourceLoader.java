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

package tech.pegasys.teku.infrastructure.io.resource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class ClasspathResourceLoader implements ResourceLoader {

  private final Class<?> referenceClass;
  private final Function<String, List<String>> nameToFilenameMapper;
  private final Collection<String> availableResourceNames;

  public ClasspathResourceLoader(
      final Class<?> referenceClass,
      final Function<String, List<String>> nameToFilenameMapper,
      final String... availableResourceNames) {
    this.referenceClass = referenceClass;
    this.nameToFilenameMapper = nameToFilenameMapper;
    this.availableResourceNames = Set.of(availableResourceNames);
  }

  @Override
  public Optional<InputStream> load(final String source) {
    Optional<InputStream> resource = Optional.empty();
    if (!availableResourceNames.contains(source)) {
      return resource;
    }

    final List<String> filenameOptions = nameToFilenameMapper.apply(source);
    for (String filenameOption : filenameOptions) {
      resource = Optional.ofNullable(referenceClass.getResourceAsStream(filenameOption));
      if (resource.isPresent()) {
        break;
      }
    }

    return resource;
  }

  @Override
  public List<InputStream> loadAll(final String source) {
    List<InputStream> resources = new ArrayList<>();
    if (!availableResourceNames.contains(source)) {
      return resources;
    }

    final List<String> filenameOptions = nameToFilenameMapper.apply(source);
    for (String filenameOption : filenameOptions) {
      Optional.ofNullable(referenceClass.getResourceAsStream(filenameOption))
          .ifPresent(resources::add);
    }

    return resources;
  }
}
