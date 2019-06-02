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

package tech.pegasys.artemis.metrics;

import io.prometheus.client.vertx.MetricsHandler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

/**
 * Registers a Prometheus REST endpoint that can be called remotely by Prometheus to gather metrics.
 */
public final class PrometheusEndpoint {

  /**
   * Creates a new endpoint for the network interface and port provided. The endpoint lifecycle is
   * tied to the lifecycle of the Vertx object.
   *
   * @param vertx the Vert.x instance
   * @param networkInterface the network interface to bind the endpoint to
   * @param port the port to bind the endpoint to
   */
  public static void registerEndpoint(Vertx vertx, String networkInterface, int port) {
    HttpServer metricsServer = vertx.createHttpServer();
    MetricsHandler handler = new MetricsHandler();
    Router router = Router.router(vertx);
    router.route().handler(handler);
    metricsServer.requestHandler(router).listen(port, networkInterface);
  }
}
