/*
 * Copyright 2019 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.xds.internal.sds;

import static com.google.common.truth.Truth.assertThat;
import static io.grpc.xds.internal.sds.CommonTlsContextTestsUtil.CA_PEM_FILE;
import static io.grpc.xds.internal.sds.CommonTlsContextTestsUtil.CLIENT_KEY_FILE;
import static io.grpc.xds.internal.sds.CommonTlsContextTestsUtil.CLIENT_PEM_FILE;
import static io.grpc.xds.internal.sds.CommonTlsContextTestsUtil.SERVER_0_KEY_FILE;
import static io.grpc.xds.internal.sds.CommonTlsContextTestsUtil.SERVER_0_PEM_FILE;
import static io.grpc.xds.internal.sds.CommonTlsContextTestsUtil.SERVER_1_KEY_FILE;
import static io.grpc.xds.internal.sds.CommonTlsContextTestsUtil.SERVER_1_PEM_FILE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.xds.EnvoyServerProtoData.DownstreamTlsContext;
import io.grpc.xds.EnvoyServerProtoData.UpstreamTlsContext;
import io.grpc.xds.internal.sds.ReferenceCountingMap.ValueFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link TlsContextManagerImpl}. */
@RunWith(JUnit4.class)
public class TlsContextManagerTest {

  @Rule public final MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ValueFactory<UpstreamTlsContext, SslContextProvider> mockClientFactory;

  @Mock ValueFactory<DownstreamTlsContext, SslContextProvider> mockServerFactory;

  @Test
  public void createServerSslContextProvider() {
    DownstreamTlsContext downstreamTlsContext =
        CommonTlsContextTestsUtil.buildDownstreamTlsContextFromFilenames(
            SERVER_1_KEY_FILE, SERVER_1_PEM_FILE, /* trustCa= */ null);

    TlsContextManagerImpl tlsContextManagerImpl = new TlsContextManagerImpl(null);
    SslContextProvider serverSecretProvider =
        tlsContextManagerImpl.findOrCreateServerSslContextProvider(downstreamTlsContext);
    assertThat(serverSecretProvider).isNotNull();

    SslContextProvider serverSecretProvider1 =
        tlsContextManagerImpl.findOrCreateServerSslContextProvider(downstreamTlsContext);
    assertThat(serverSecretProvider1).isSameInstanceAs(serverSecretProvider);
  }

  @Test
  public void createClientSslContextProvider() {
    UpstreamTlsContext upstreamTlsContext =
        CommonTlsContextTestsUtil.buildUpstreamTlsContextFromFilenames(
            /* privateKey= */ null, /* certChain= */ null, CA_PEM_FILE);

    TlsContextManagerImpl tlsContextManagerImpl = new TlsContextManagerImpl(null);
    SslContextProvider clientSecretProvider =
        tlsContextManagerImpl.findOrCreateClientSslContextProvider(upstreamTlsContext);
    assertThat(clientSecretProvider).isNotNull();

    SslContextProvider clientSecretProvider1 =
        tlsContextManagerImpl.findOrCreateClientSslContextProvider(upstreamTlsContext);
    assertThat(clientSecretProvider1).isSameInstanceAs(clientSecretProvider);
  }

  @Test
  public void createServerSslContextProvider_differentInstance() {
    DownstreamTlsContext downstreamTlsContext =
        CommonTlsContextTestsUtil.buildDownstreamTlsContextFromFilenames(
            SERVER_1_KEY_FILE, SERVER_1_PEM_FILE, /* trustCa= */ null);

    TlsContextManagerImpl tlsContextManagerImpl = new TlsContextManagerImpl(null);
    SslContextProvider serverSecretProvider =
        tlsContextManagerImpl.findOrCreateServerSslContextProvider(downstreamTlsContext);
    assertThat(serverSecretProvider).isNotNull();

    DownstreamTlsContext downstreamTlsContext1 =
        CommonTlsContextTestsUtil.buildDownstreamTlsContextFromFilenames(
            SERVER_0_KEY_FILE, SERVER_0_PEM_FILE, CA_PEM_FILE);
    SslContextProvider serverSecretProvider1 =
        tlsContextManagerImpl.findOrCreateServerSslContextProvider(downstreamTlsContext1);
    assertThat(serverSecretProvider1).isNotNull();
    assertThat(serverSecretProvider1).isNotSameInstanceAs(serverSecretProvider);
  }

  @Test
  public void createClientSslContextProvider_differentInstance() {
    UpstreamTlsContext upstreamTlsContext =
        CommonTlsContextTestsUtil.buildUpstreamTlsContextFromFilenames(
            /* privateKey= */ null, /* certChain= */ null, CA_PEM_FILE);

    TlsContextManagerImpl tlsContextManagerImpl = new TlsContextManagerImpl(null);
    SslContextProvider clientSecretProvider =
        tlsContextManagerImpl.findOrCreateClientSslContextProvider(upstreamTlsContext);
    assertThat(clientSecretProvider).isNotNull();

    UpstreamTlsContext upstreamTlsContext1 =
        CommonTlsContextTestsUtil.buildUpstreamTlsContextFromFilenames(
            CLIENT_KEY_FILE, CLIENT_PEM_FILE, CA_PEM_FILE);

    SslContextProvider clientSecretProvider1 =
        tlsContextManagerImpl.findOrCreateClientSslContextProvider(upstreamTlsContext1);
    assertThat(clientSecretProvider1).isNotSameInstanceAs(clientSecretProvider);
  }

  @Test
  public void createServerSslContextProvider_releaseInstance() {
    DownstreamTlsContext downstreamTlsContext =
        CommonTlsContextTestsUtil.buildDownstreamTlsContextFromFilenames(
            SERVER_1_KEY_FILE, SERVER_1_PEM_FILE, /* trustCa= */ null);

    TlsContextManagerImpl tlsContextManagerImpl =
        new TlsContextManagerImpl(mockClientFactory, mockServerFactory);
    SslContextProvider mockProvider = mock(SslContextProvider.class);
    when(mockServerFactory.create(downstreamTlsContext)).thenReturn(mockProvider);
    SslContextProvider serverSecretProvider =
        tlsContextManagerImpl.findOrCreateServerSslContextProvider(downstreamTlsContext);
    assertThat(serverSecretProvider).isSameInstanceAs(mockProvider);
    verify(mockProvider, never()).close();
    when(mockProvider.getDownstreamTlsContext()).thenReturn(downstreamTlsContext);
    tlsContextManagerImpl.releaseServerSslContextProvider(mockProvider);
    verify(mockProvider, times(1)).close();
  }

  @Test
  public void createClientSslContextProvider_releaseInstance() {
    UpstreamTlsContext upstreamTlsContext =
        CommonTlsContextTestsUtil.buildUpstreamTlsContextFromFilenames(
            CLIENT_KEY_FILE, CLIENT_PEM_FILE, CA_PEM_FILE);

    TlsContextManagerImpl tlsContextManagerImpl =
        new TlsContextManagerImpl(mockClientFactory, mockServerFactory);
    SslContextProvider mockProvider = mock(SslContextProvider.class);
    when(mockClientFactory.create(upstreamTlsContext)).thenReturn(mockProvider);
    SslContextProvider clientSecretProvider =
        tlsContextManagerImpl.findOrCreateClientSslContextProvider(upstreamTlsContext);
    assertThat(clientSecretProvider).isSameInstanceAs(mockProvider);
    verify(mockProvider, never()).close();
    when(mockProvider.getUpstreamTlsContext()).thenReturn(upstreamTlsContext);
    tlsContextManagerImpl.releaseClientSslContextProvider(mockProvider);
    verify(mockProvider, times(1)).close();
  }
}
