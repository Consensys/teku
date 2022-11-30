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

package tech.pegasys.teku.kzg.impl.ckzg;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.kzg.impl.KZG4844;

public final class CkzgLoader {

  private static final Logger LOG = LogManager.getLogger();
  public static final Optional<KZG4844> INSTANCE = loadCkzg();

  private static Optional<KZG4844> loadCkzg() {
    try {
      final Class<?> kzgClass = Class.forName("tech.pegasys.teku.kzg.impl.ckzg.CkzgKZG4844");
      return Optional.of((KZG4844) kzgClass.getDeclaredConstructor().newInstance());
    } catch (final InstantiationException
        | ExceptionInInitializerError
        | InvocationTargetException
        | NoSuchMethodException
        | IllegalAccessException
        | ClassNotFoundException e) {
      LOG.error("Couldn't load native KZG library", e);
      return Optional.empty();
    }
  }
}
