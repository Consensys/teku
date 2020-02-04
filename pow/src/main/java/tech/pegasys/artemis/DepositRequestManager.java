package tech.pegasys.artemis;

import com.google.common.primitives.UnsignedLong;
import io.reactivex.disposables.Disposable;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.util.async.SafeFuture;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;

public class DepositRequestManager {

  private final Web3j web3j;
  private final Consumer<DepositsFromBlockEvent> depositsFromBlockEventConsumer;
  private final Map<String, SafeFuture<EthBlock.Block>> cachedBlocks = new ConcurrentHashMap<>();

  private BigInteger latestSuccessfullyQueriedBlockNumber = BigInteger.ZERO;
  private Disposable newBlockSubscription;
  private BiFunction<
          DefaultBlockParameter,
          DefaultBlockParameter,
          List<DepositContract.DepositEventEventResponse>> depositEventsInRangeFunction;

  public DepositRequestManager(Web3j web3j,
                               Consumer<DepositsFromBlockEvent> depositsFromBlockEventConsumer,
                               BiFunction<
                                       DefaultBlockParameter,
                                       DefaultBlockParameter,
                                       List<DepositContract.DepositEventEventResponse>> depositEventsInRangeFunction) {
    this.web3j = web3j;
    this.depositsFromBlockEventConsumer = depositsFromBlockEventConsumer;
    this.depositEventsInRangeFunction = depositEventsInRangeFunction;
  }

  public void start() {
    newBlockSubscription = web3j
            .blockFlowable(false)
            .map(EthBlock::getBlock)
            .map(EthBlock.Block::getNumber)
            .map(number -> number.subtract(ETH1_FOLLOW_DISTANCE.bigIntegerValue()))
            .subscribe(this::getLatestDeposits);
  }

  public void stop() {
    newBlockSubscription.dispose();
  }

  private void getLatestDeposits(BigInteger latestCanonicalBlockNumber) {
    DefaultBlockParameter toBlock = DefaultBlockParameter.valueOf(latestCanonicalBlockNumber);
    DefaultBlockParameter fromBlock;
    if (latestSuccessfullyQueriedBlockNumber.equals(BigInteger.ZERO)) {
      fromBlock = DefaultBlockParameterName.EARLIEST;
    } else {
      fromBlock = DefaultBlockParameter.valueOf(latestSuccessfullyQueriedBlockNumber);
    }

    List<DepositContract.DepositEventEventResponse> events = depositEventsInRangeFunction.apply(fromBlock, toBlock);

    List<SafeFuture<DepositsFromBlockEvent>> depositsFromEventFutures = events.stream()
            .collect(Collectors.groupingBy(event -> event.log.getBlockNumber(), TreeMap::new, Collectors.toList()))
            .values()
            .stream()
            .map(depositsEventResponseList -> {
              List<Deposit> depositsList = depositsEventResponseList.stream().map(Deposit::new).collect(Collectors.toList());
              return SafeFuture.of(web3j.ethGetBlockByHash(
                      depositsEventResponseList.get(0).log.getBlockHash(),
                      false
              )
                      .sendAsync()
                      .thenApply(EthBlock::getBlock)
                      .thenApply(block -> new DepositsFromBlockEvent(
                              UnsignedLong.valueOf(block.getNumber()),
                              Bytes32.fromHexString(block.getHash()),
                              UnsignedLong.valueOf(block.getTimestamp()),
                              depositsList)));
            }).collect(Collectors.toList());

    SafeFuture.allOf(depositsFromEventFutures.toArray(SafeFuture[]::new))
            .thenRun(() -> depositsFromEventFutures
                    .stream()
                    .map(future -> checkNotNull(future.getNow(null)))
                    .sorted(Comparator.comparing(DepositsFromBlockEvent::getBlockNumber))
                    .forEachOrdered(this::publishDeposits))
            .thenRun(() -> latestSuccessfullyQueriedBlockNumber = latestCanonicalBlockNumber);
  }

  private void publishDeposits(DepositsFromBlockEvent event) {
    depositsFromBlockEventConsumer.accept(event);
  }
}
