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

package tech.pegasys.artemis.util.types;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Result<S, E> {

  public static <S, E> Result<S, E> success(final S successValue) {
    return new SuccessResult<>(successValue);
  }

  public static <S, E> Result<S, E> error(final E errorValue) {
    return new ErrorResult<>(errorValue);
  }

  public abstract void either(Consumer<S> onSuccess, Consumer<E> onError);

  public abstract <T> T mapEither(Function<S, T> mapSuccess, Function<E, T> mapError);

  public <T> Result<T, E> map(final Function<S, T> mapSuccess) {
    return mapEither(mapSuccess.andThen(Result::success), Result::error);
  }

  public <T> Result<T, E> flatMap(final Function<S, Result<T, E>> mapSuccess) {
    return mapEither(mapSuccess, Result::error);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + mapEither(Objects::toString, Objects::toString) + ")";
  }

  private static class SuccessResult<S, E> extends Result<S, E> {

    private final S value;

    private SuccessResult(final S value) {
      this.value = value;
    }

    @Override
    public void either(final Consumer<S> onSuccess, final Consumer<E> onError) {
      onSuccess.accept(value);
    }

    @Override
    public <T> T mapEither(final Function<S, T> mapSuccess, final Function<E, T> mapError) {
      return mapSuccess.apply(value);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SuccessResult<?, ?> that = (SuccessResult<?, ?>) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  private static class ErrorResult<S, E> extends Result<S, E> {
    private final E value;

    private ErrorResult(final E value) {
      this.value = value;
    }

    @Override
    public void either(final Consumer<S> onSuccess, final Consumer<E> onError) {
      onError.accept(value);
    }

    @Override
    public <T> T mapEither(final Function<S, T> mapSuccess, final Function<E, T> mapError) {
      return mapError.apply(value);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ErrorResult<?, ?> that = (ErrorResult<?, ?>) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }
}
