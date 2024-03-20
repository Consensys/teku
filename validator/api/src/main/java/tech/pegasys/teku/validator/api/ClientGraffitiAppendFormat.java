/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.api;

public enum ClientGraffitiAppendFormat {
  // Appends comprehensive client info if there is a space for it. Reduces verbosity when needed.
  // Client info is separated with a space after user's graffiti if any.
  AUTO,
  // Prepends comprehensive client info with a space before user's graffiti if there is a room for
  // it.
  NAME,
  // Client information is not appended to the graffiti.
  NONE;
}
