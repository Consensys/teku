package tech.pegasys.artemis.proto.messagesigner;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 * <pre>
 * The greeting service definition.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.21.0)",
    comments = "Source: messagesigner.proto")
public final class MessageSignerGrpc {

  private MessageSignerGrpc() {}

  public static final String SERVICE_NAME = "messagesigner.MessageSigner";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<tech.pegasys.artemis.proto.messagesigner.MessageSignRequest,
      tech.pegasys.artemis.proto.messagesigner.MessageSignResponse> getSignMessageMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "signMessage",
      requestType = tech.pegasys.artemis.proto.messagesigner.MessageSignRequest.class,
      responseType = tech.pegasys.artemis.proto.messagesigner.MessageSignResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<tech.pegasys.artemis.proto.messagesigner.MessageSignRequest,
      tech.pegasys.artemis.proto.messagesigner.MessageSignResponse> getSignMessageMethod() {
    io.grpc.MethodDescriptor<tech.pegasys.artemis.proto.messagesigner.MessageSignRequest, tech.pegasys.artemis.proto.messagesigner.MessageSignResponse> getSignMessageMethod;
    if ((getSignMessageMethod = MessageSignerGrpc.getSignMessageMethod) == null) {
      synchronized (MessageSignerGrpc.class) {
        if ((getSignMessageMethod = MessageSignerGrpc.getSignMessageMethod) == null) {
          MessageSignerGrpc.getSignMessageMethod = getSignMessageMethod = 
              io.grpc.MethodDescriptor.<tech.pegasys.artemis.proto.messagesigner.MessageSignRequest, tech.pegasys.artemis.proto.messagesigner.MessageSignResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "messagesigner.MessageSigner", "signMessage"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  tech.pegasys.artemis.proto.messagesigner.MessageSignRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  tech.pegasys.artemis.proto.messagesigner.MessageSignResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new MessageSignerMethodDescriptorSupplier("signMessage"))
                  .build();
          }
        }
     }
     return getSignMessageMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static MessageSignerStub newStub(io.grpc.Channel channel) {
    return new MessageSignerStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static MessageSignerBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new MessageSignerBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static MessageSignerFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new MessageSignerFutureStub(channel);
  }

  /**
   * <pre>
   * The greeting service definition.
   * </pre>
   */
  public static abstract class MessageSignerImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Sends a greeting
     * </pre>
     */
    public void signMessage(tech.pegasys.artemis.proto.messagesigner.MessageSignRequest request,
        io.grpc.stub.StreamObserver<tech.pegasys.artemis.proto.messagesigner.MessageSignResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getSignMessageMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getSignMessageMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                tech.pegasys.artemis.proto.messagesigner.MessageSignRequest,
                tech.pegasys.artemis.proto.messagesigner.MessageSignResponse>(
                  this, METHODID_SIGN_MESSAGE)))
          .build();
    }
  }

  /**
   * <pre>
   * The greeting service definition.
   * </pre>
   */
  public static final class MessageSignerStub extends io.grpc.stub.AbstractStub<MessageSignerStub> {
    private MessageSignerStub(io.grpc.Channel channel) {
      super(channel);
    }

    private MessageSignerStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MessageSignerStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new MessageSignerStub(channel, callOptions);
    }

    /**
     * <pre>
     * Sends a greeting
     * </pre>
     */
    public void signMessage(tech.pegasys.artemis.proto.messagesigner.MessageSignRequest request,
        io.grpc.stub.StreamObserver<tech.pegasys.artemis.proto.messagesigner.MessageSignResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getSignMessageMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * <pre>
   * The greeting service definition.
   * </pre>
   */
  public static final class MessageSignerBlockingStub extends io.grpc.stub.AbstractStub<MessageSignerBlockingStub> {
    private MessageSignerBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private MessageSignerBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MessageSignerBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new MessageSignerBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Sends a greeting
     * </pre>
     */
    public tech.pegasys.artemis.proto.messagesigner.MessageSignResponse signMessage(tech.pegasys.artemis.proto.messagesigner.MessageSignRequest request) {
      return blockingUnaryCall(
          getChannel(), getSignMessageMethod(), getCallOptions(), request);
    }
  }

  /**
   * <pre>
   * The greeting service definition.
   * </pre>
   */
  public static final class MessageSignerFutureStub extends io.grpc.stub.AbstractStub<MessageSignerFutureStub> {
    private MessageSignerFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private MessageSignerFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MessageSignerFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new MessageSignerFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Sends a greeting
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<tech.pegasys.artemis.proto.messagesigner.MessageSignResponse> signMessage(
        tech.pegasys.artemis.proto.messagesigner.MessageSignRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getSignMessageMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SIGN_MESSAGE = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final MessageSignerImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(MessageSignerImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SIGN_MESSAGE:
          serviceImpl.signMessage((tech.pegasys.artemis.proto.messagesigner.MessageSignRequest) request,
              (io.grpc.stub.StreamObserver<tech.pegasys.artemis.proto.messagesigner.MessageSignResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class MessageSignerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    MessageSignerBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return tech.pegasys.artemis.proto.messagesigner.MessageSignerProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("MessageSigner");
    }
  }

  private static final class MessageSignerFileDescriptorSupplier
      extends MessageSignerBaseDescriptorSupplier {
    MessageSignerFileDescriptorSupplier() {}
  }

  private static final class MessageSignerMethodDescriptorSupplier
      extends MessageSignerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    MessageSignerMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (MessageSignerGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new MessageSignerFileDescriptorSupplier())
              .addMethod(getSignMessageMethod())
              .build();
        }
      }
    }
    return result;
  }
}
