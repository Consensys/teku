package tech.pegasys.artemis.test.acceptance.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;
import tech.pegasys.artemis.util.Waiter;

public class ArtemisDepositSender extends AbstractArtemisNode {

  private static final String VALIDATORS_YAML_PATH = "/validators.yaml";

  public ArtemisDepositSender(final Network network) {
    super(network);
  }

  public void sendValidatorDeposits(final BesuNode eth1Node, final int numberOfValidators) {
    container.setWaitStrategy(
        new WaitStrategy() {
          @Override
          public void waitUntilReady(final WaitStrategyTarget waitStrategyTarget) {}

          @Override
          public WaitStrategy withStartupTimeout(final Duration startupTimeout) {
            return this;
          }
        });
    container.setCommand(
        "validator",
        "generate",
        "--contract-address",
        eth1Node.getDepositContractAddress(),
        "--number-of-validators",
        Integer.toString(numberOfValidators),
        "--private-key",
        eth1Node.getRichBenefactorKey(),
        "--node-url",
        eth1Node.getInternalJsonRpcUrl());
    container.start();
    Waiter.waitFor(() -> assertThat(container.isRunning()).isFalse());
    System.out.println(container.getLogs());
    container.stop();
  }
}
