package com.google.grpc.cloudprober;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.ListValue;
import com.google.protobuf.Value;
import com.google.spanner.v1.CommitResponse;
import com.google.spanner.v1.ListSessionsResponse;
import com.google.spanner.v1.PartialResultSet;
import com.google.spanner.v1.PartitionResponse;
import com.google.spanner.v1.ResultSet;
import com.google.spanner.v1.Session;
import com.google.spanner.v1.SpannerGrpc;
import com.google.spanner.v1.Transaction;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for Spanner probes. */
@RunWith(JUnit4.class)
public class SpannerProbesTest {
  private static final String DATABASE =
      "projects/cloudprober-test/instances/test-instance/databases/test-db";
  private static final String SESSION_NAME = DATABASE + "/sessions/s1";
  private static final String TEST_USERNAME = "test_username";

  private Session session;
  private Map<String, Long> metrics;
  private SpannerGrpc.SpannerBlockingStub stub;
  private Empty emptyResponse;
  private MockSpannerImpl serviceImpl;

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  @Before
  public void setUp() throws Exception {
    // Set up the mockito spy so that we can trace if the method on the server side is called.
    serviceImpl = Mockito.spy(new MockSpannerImpl());

    // Set up the fake server and the channel, get the stub.
    String serverName = InProcessServerBuilder.generateName();
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(serviceImpl)
            .build()
            .start());
    ManagedChannel channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
    stub = SpannerGrpc.newBlockingStub(channel);

    // Set up the rules during the tests.
    session = Session.newBuilder().setName(SESSION_NAME).build();
    emptyResponse = Empty.newBuilder().build();
    metrics = new HashMap<>();
  }

  @Test
  public void testSessionManagement() throws Exception {
    ListSessionsResponse sessions = ListSessionsResponse.newBuilder().addSessions(session).build();

    serviceImpl.addResponse(session);
    serviceImpl.addResponse(session);
    serviceImpl.addResponse(sessions);
    serviceImpl.addResponse(emptyResponse);

    SpannerProbes.sessionManagementProber(stub, metrics);
    verify(serviceImpl, times(1)).createSession(any(), any());
    verify(serviceImpl, times(1)).getSession(any(), any());
    verify(serviceImpl, times(1)).listSessions(any(), any());
    verify(serviceImpl, times(1)).deleteSession(any(), any());
    assertThat(metrics.size()).isEqualTo(4);
  }

  @Test
  public void testExecuteSql() throws Exception {
    Value value = Value.newBuilder().setStringValue(TEST_USERNAME).build();
    ListValue row = ListValue.newBuilder().addValues(value).build();
    ResultSet response = ResultSet.newBuilder().addRows(row).build();
    PartialResultSet responseList = PartialResultSet.newBuilder().addValues(value).build();

    serviceImpl.addResponse(session);
    serviceImpl.addResponse(response);
    serviceImpl.addResponse(responseList);
    serviceImpl.addResponse(emptyResponse);

    SpannerProbes.executeSqlProber(stub, metrics);
    verify(serviceImpl, times(1)).createSession(any(), any());
    verify(serviceImpl, times(1)).executeSql(any(), any());
    verify(serviceImpl, times(1)).executeStreamingSql(any(), any());
    verify(serviceImpl, times(1)).deleteSession(any(), any());
    assertThat(metrics.size()).isEqualTo(2);
  }

  @Test
  public void testRead() throws Exception {
    Value value = Value.newBuilder().setStringValue(TEST_USERNAME).build();
    ListValue row = ListValue.newBuilder().addValues(value).build();
    ResultSet response = ResultSet.newBuilder().addRows(row).build();
    PartialResultSet responseList = PartialResultSet.newBuilder().addValues(value).build();

    serviceImpl.addResponse(session);
    serviceImpl.addResponse(response);
    serviceImpl.addResponse(responseList);
    serviceImpl.addResponse(emptyResponse);

    SpannerProbes.readProber(stub, metrics);
    verify(serviceImpl, times(1)).createSession(any(), any());
    verify(serviceImpl, times(1)).read(any(), any());
    verify(serviceImpl, times(1)).streamingRead(any(), any());
    verify(serviceImpl, times(1)).deleteSession(any(), any());
    assertThat(metrics.size()).isEqualTo(2);
  }

  @Test
  public void testTransaction() throws Exception {
    ByteString id = ByteString.copyFromUtf8("27");
    Transaction txn = Transaction.newBuilder().setId(id).build();
    CommitResponse responseCommit = CommitResponse.newBuilder().build();
    serviceImpl.addResponse(session);
    serviceImpl.addResponse(txn);
    serviceImpl.addResponse(responseCommit);
    serviceImpl.addResponse(txn);
    serviceImpl.addResponse(emptyResponse);
    serviceImpl.addResponse(emptyResponse);

    SpannerProbes.transactionProber(stub, metrics);
    verify(serviceImpl, times(1)).createSession(any(), any());
    verify(serviceImpl, times(2)).beginTransaction(any(), any());
    verify(serviceImpl, times(1)).rollback(any(), any());
    verify(serviceImpl, times(1)).commit(any(), any());
    verify(serviceImpl, times(1)).deleteSession(any(), any());
    assertThat(metrics.size()).isEqualTo(3);
  }

  @Test
  public void testPartition() throws Exception {
    PartitionResponse responsePartition = PartitionResponse.newBuilder().build();

    serviceImpl.addResponse(session);
    serviceImpl.addResponse(responsePartition);
    serviceImpl.addResponse(responsePartition);
    serviceImpl.addResponse(emptyResponse);

    SpannerProbes.partitionProber(stub, metrics);
    verify(serviceImpl, times(1)).createSession(any(), any());
    verify(serviceImpl, times(1)).partitionQuery(any(), any());
    verify(serviceImpl, times(1)).partitionRead(any(), any());
    verify(serviceImpl, times(1)).deleteSession(any(), any());
    assertThat(metrics.size()).isEqualTo(2);
  }
}
