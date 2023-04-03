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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.spec.SpecMilestone;

public class Response<T> {
  private static final Logger LOG = LogManager.getLogger();

  private final T payload;
  private final String errorMessage;

  public Response(final T payload, final String errorMessage) {
    this.payload = payload;
    this.errorMessage = errorMessage;
  }

  public Response(final T payload) {
    this.payload = payload;
    this.errorMessage = null;
  }

  public static <T> Response<T> withNullPayload() {
    return new Response<>(null, null);
  }

  public static <T> Response<T> withErrorMessage(final String errorMessage) {
    return new Response<>(null, errorMessage);
  }

  public static <T, R> Response<R> unwrapVersioned(
      final Response<T> response,
      final Function<T, R> unwrapFunction,
      final SpecMilestone expectedMilestone,
      final Function<T, SpecMilestone> unwrapVersionFunction,
      final boolean strictVersionCheck) {

    if (response.isFailure()) {
      return Response.withErrorMessage(response.getErrorMessage());
    }
    final T payload = response.getPayload();
    if (payload == null) {
      return Response.withNullPayload();
    }

    final SpecMilestone receivedMilestone = unwrapVersionFunction.apply(payload);

    final boolean milestonesMismatch = !receivedMilestone.equals(expectedMilestone);

    if (milestonesMismatch) {
      if (strictVersionCheck) {
        throw new IllegalArgumentException(
            "Wrong response version: expected "
                + expectedMilestone
                + ", received "
                + receivedMilestone);
      } else {
        LOG.warn(
            "Wrong response version: expected {}, received {}.",
            expectedMilestone,
            receivedMilestone);
      }
    }

    return new Response<>(unwrapFunction.apply(payload));
  }

  public static <T> Response<Optional<T>> convertToOptional(final Response<T> response) {
    if (response.isFailure()) {
      return Response.withErrorMessage(response.getErrorMessage());
    }
    final T payload = response.getPayload();
    return payload == null
        ? new Response<>(Optional.empty())
        : new Response<>(Optional.of(payload));
  }

  public T getPayload() {
    return payload;
  }

  public String getErrorMessage() {
    if (errorMessage != null) {
      return errorMessage.strip();
    }
    return null;
  }

  public boolean isSuccess() {
    return errorMessage == null;
  }

  public boolean isFailure() {
    return errorMessage != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Response<?> response = (Response<?>) o;
    return Objects.equals(payload, response.payload)
        && Objects.equals(errorMessage, response.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(payload, errorMessage);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("payload", payload)
        .add("errorMessage", errorMessage)
        .toString();
  }
}
