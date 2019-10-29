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

package org.ethereum.beacon.consensus;

public enum TransitionType {
  INITIAL,
  SLOT,
  EPOCH,
  BLOCK,
  UNKNOWN;

  public boolean canBeAppliedAfter(TransitionType previousTransition) {
    switch (previousTransition) {
      case UNKNOWN:
        return true;
      case INITIAL:
        switch (this) {
          case INITIAL:
            return false;
          case EPOCH:
            return false;
          case SLOT:
            return true;
          case BLOCK:
            return false;
        }
      case EPOCH:
        switch (this) {
          case INITIAL:
            return false;
          case EPOCH:
            return false;
          case SLOT:
            return true;
          case BLOCK:
            return false;
        }
      case SLOT:
        switch (this) {
          case INITIAL:
            return false;
          case EPOCH:
            return true;
          case SLOT:
            return true;
          case BLOCK:
            return true;
        }
      case BLOCK:
        switch (this) {
          case INITIAL:
            return false;
          case EPOCH:
            return false;
          case SLOT:
            return true;
          case BLOCK:
            return false;
        }
    }
    throw new RuntimeException("Impossible");
  }

  public void checkCanBeAppliedAfter(TransitionType previousTransition) throws RuntimeException {
    if (!canBeAppliedAfter(previousTransition)) {
      throw new RuntimeException(
          this + " transition can't be applied after " + previousTransition + " transition");
    }
  }
}
