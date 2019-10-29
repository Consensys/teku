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

package org.ethereum.beacon.consensus.verifier;

/**
 * Helper class to hold verification result.
 *
 * <p>Basically, there are two meaningful arguments for this helper. A flag that denotes whether
 * verification has been successful or not. And a message which contains verification details and
 * depends on verifier implementation.
 *
 * @see BeaconBlockVerifier
 * @see BeaconStateVerifier
 */
public class VerificationResult {

  /** Successfully passed verification. */
  public static final VerificationResult PASSED = new VerificationResult("", true);

  private final String message;
  private final boolean passed;

  public VerificationResult(String message, boolean passed) {
    this.message = message;
    this.passed = passed;
  }

  /**
   * Factory method that creates result of unsuccessful verification with given message.
   *
   * @param format a message format which is passed to {@link String#format(String, Object...)}.
   * @param args args to {@link String#format(String, Object...)}
   * @return failed result with given message
   */
  public static VerificationResult failedResult(String format, Object... args) {
    return new VerificationResult(String.format(format, args), false);
  }

  public boolean isPassed() {
    return passed;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return isPassed() ? "OK" : "FAILED: " + getMessage();
  }
}
