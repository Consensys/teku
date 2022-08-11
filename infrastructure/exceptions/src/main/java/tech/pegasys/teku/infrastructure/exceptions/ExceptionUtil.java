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

package tech.pegasys.teku.infrastructure.exceptions;

import java.util.Optional;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class ExceptionUtil {

  @SuppressWarnings("unchecked")
  public static <T extends Throwable> Optional<T> getCause(
      final Throwable err, Class<? extends T> targetType) {
    return ExceptionUtils.getThrowableList(err).stream()
        .filter(targetType::isInstance)
        .map(e -> (T) e)
        .findFirst();
  }

  public static <T extends Throwable> boolean hasCause(
      final Throwable err, Class<? extends T> targetType) {
    return getCause(err, targetType).isPresent();
  }

  @SafeVarargs
  public static boolean hasCause(
      final Throwable err, final Class<? extends Throwable>... targetTypes) {

    return ExceptionUtils.getThrowableList(err).stream()
        .anyMatch(cause -> isAnyOf(cause, targetTypes));
  }

  @SafeVarargs
  private static boolean isAnyOf(
      final Throwable cause, final Class<? extends Throwable>... targetTypes) {
    for (Class<? extends Throwable> targetType : targetTypes) {
      if (targetType.isInstance(cause)) {
        return true;
      }
    }
    return false;
  }

  public static String getMessageOrSimpleName(final Throwable throwable) {
    return Optional.ofNullable(throwable.getMessage()).orElse(throwable.getClass().getSimpleName());
  }
}
