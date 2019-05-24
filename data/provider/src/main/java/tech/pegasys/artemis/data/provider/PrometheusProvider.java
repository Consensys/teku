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

package tech.pegasys.artemis.data.provider;

import io.prometheus.client.Counter;
import io.prometheus.client.vertx.MetricsHandler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import tech.pegasys.artemis.data.IRecordAdapter;

/** Records metrics as prometheus metrics that can be then read by a Prometheus server. */
public final class PrometheusProvider implements FileProvider {

  private final HttpServer metricsServer;

  private final Counter epochCounter =
      Counter.build()
          .name("epoch")
          .help("Epoch counter")
          .labelNames(
              "index",
              "slot",
              "epoch",
              "last_finalized_block_root",
              "last_finalized_state_root",
              "block_parent_root",
              "validators",
              "last_justified_block_root",
              "last_justified_state_root")
          .register();

  public PrometheusProvider(Vertx vertx, String networkInterface, int port) {
    metricsServer = vertx.createHttpServer();
    MetricsHandler handler = new MetricsHandler();
    Router router = Router.router(vertx);
    router.route().handler(handler);
    metricsServer.requestHandler(router).listen(port, networkInterface);
  }

  @Override
  public void serialOutput(IRecordAdapter record) {
    epochCounter.labels(record.toLabels()).inc();
  }

  @Override
  public void formattedOutput(IRecordAdapter record) {
    epochCounter.labels(record.toLabels()).inc();
  }

  @Override
  public void close() {
    metricsServer.close();
  }
}
