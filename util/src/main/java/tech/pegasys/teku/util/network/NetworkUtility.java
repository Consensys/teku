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

package tech.pegasys.teku.util.network;

public class NetworkUtility {
  public static final String INADDR_ANY = "0.0.0.0";
  public static final String INADDR6_ANY = "0:0:0:0:0:0:0:0";

  public static boolean isUnspecifiedAddress(final String ipAddress) {
    return INADDR_ANY.equals(ipAddress) || INADDR6_ANY.equals(ipAddress);
  }
}
