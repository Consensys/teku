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

package tech.pegasys.teku.infrastructure.http;

public class HttpStatusCodes {

  public static final int SC_OK = 200;
  public static final int SC_ACCEPTED = 202;
  public static final int SC_NO_CONTENT = 204;
  public static final int SC_PARTIAL_CONTENT = 206;
  public static final int SC_BAD_REQUEST = 400;
  public static final int SC_FORBIDDEN = 403;
  public static final int SC_NOT_FOUND = 404;
  public static final int SC_TOO_MANY_REQUESTS = 429;
  public static final int SC_INTERNAL_SERVER_ERROR = 500;
  public static final int SC_SERVICE_UNAVAILABLE = 503;
}
