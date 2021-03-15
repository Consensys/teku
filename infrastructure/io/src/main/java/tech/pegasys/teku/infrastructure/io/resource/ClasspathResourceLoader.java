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
import java.util.Optional;
import java.util.function.Predicate;

public class ClasspathResourceLoader extends ResourceLoader {

  private final Class<?> referenceClass;

  public ClasspathResourceLoader(
      final Class<?> referenceClass, final Predicate<String> resourceFilter) {
    super(resourceFilter);
    this.referenceClass = referenceClass;
  }

  @Override
  Optional<InputStream> loadSource(final String source) {
    return Optional.ofNullable(referenceClass.getResourceAsStream(source));
  }
}
