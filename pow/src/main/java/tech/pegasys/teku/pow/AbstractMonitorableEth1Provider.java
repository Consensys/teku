/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.pow;

import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public abstract class AbstractMonitorableEth1Provider implements MonitorableEth1Provider {
  protected enum Result {
    SUCCESS,
    FAILED
  }

  private final TimeProvider timeProvider;
  protected UInt64 lastCallTime = UInt64.ZERO;
  protected UInt64 lastValidationTime = UInt64.ZERO;
  protected Result lastCallResult = Result.FAILED;
  protected Result lastValidationResult = Result.FAILED;

  protected AbstractMonitorableEth1Provider(TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
  }

  protected synchronized void updateLastValidation(Result result) {
    lastValidationTime = timeProvider.getTimeInSeconds();
    lastValidationResult = result;
  }

  protected synchronized void updateLastCall(Result result) {
    lastCallTime = timeProvider.getTimeInSeconds();
    lastCallResult = result;
  }

  @Override
  public synchronized UInt64 getLastCallTime() {
    return lastCallTime;
  }

  @Override
  public synchronized UInt64 getLastValidationTime() {
    return lastValidationTime;
  }

  @Override
  public synchronized boolean isValid() {
    return lastCallResult.equals(Result.SUCCESS) && lastValidationResult.equals(Result.SUCCESS);
  }

  @Override
  public synchronized boolean needsToBeValidated() {
    UInt64 currentTime = timeProvider.getTimeInSeconds();
    switch (lastCallResult) {
      case FAILED:
        return currentTime.isGreaterThanOrEqualTo(
            lastValidationTime.plus(Constants.ETH1_FAILED_ENDPOINT_CHECK_INTERVAL.toSeconds()));
      case SUCCESS:
        switch (lastValidationResult) {
          case FAILED:
            return currentTime.isGreaterThanOrEqualTo(
                lastValidationTime.plus(
                    Constants.ETH1_INVALID_ENDPOINT_CHECK_INTERVAL.toSeconds()));
          case SUCCESS:
            return currentTime.isGreaterThanOrEqualTo(
                lastValidationTime.plus(Constants.ETH1_VALID_ENDPOINT_CHECK_INTERVAL.toSeconds()));
          default:
            throw new IllegalStateException("Unknown result type: " + lastValidationResult);
        }
      default:
        throw new IllegalStateException("Unknown result type: " + lastCallResult);
    }
  }
}
