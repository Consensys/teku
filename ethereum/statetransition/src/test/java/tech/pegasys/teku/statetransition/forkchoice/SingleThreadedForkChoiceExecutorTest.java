package tech.pegasys.teku.statetransition.forkchoice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SingleThreadedForkChoiceExecutorTest {

  private ForkChoiceExecutor executor;

  @BeforeEach
  void setUp() {
    executor = SingleThreadedForkChoiceExecutor.create();
  }

  @AfterEach
  void tearDown() {
    executor.stop();
  }

  @Test
  void shouldPerformTasks() {

  }
}