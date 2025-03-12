/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.common.statetransition.exceptions;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

public final class ExecutionPayloadProcessingException extends Exception {

  public ExecutionPayloadProcessingException(final String message) {
    super(message);
  }

  @FormatMethod
  public ExecutionPayloadProcessingException(@FormatString final String format, Object... args) {
    super(String.format(format, args));
  }

  public ExecutionPayloadProcessingException(final Exception ex) {
    super(ex);
  }
}
