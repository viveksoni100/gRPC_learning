/*
 * Copyright 2020 The gRPC Authors
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

package io.grpc.xds;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UInt32Value;
import io.envoyproxy.envoy.config.core.v3.Address;
import io.envoyproxy.envoy.config.core.v3.CidrRange;
import io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.envoyproxy.envoy.config.core.v3.TrafficDirection;
import io.envoyproxy.envoy.config.core.v3.TransportSocket;
import io.envoyproxy.envoy.config.listener.v3.Filter;
import io.envoyproxy.envoy.config.listener.v3.FilterChain;
import io.envoyproxy.envoy.config.listener.v3.FilterChainMatch;
import io.envoyproxy.envoy.extensions.transport_sockets.tls.v3.CommonTlsContext;
import io.envoyproxy.envoy.extensions.transport_sockets.tls.v3.SdsSecretConfig;
import io.grpc.xds.EnvoyServerProtoData.DownstreamTlsContext;
import io.grpc.xds.EnvoyServerProtoData.Listener;
import io.grpc.xds.internal.sds.CommonTlsContextTestsUtil;
import io.grpc.xds.internal.sds.SslContextProviderSupplier;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link EnvoyServerProtoData}.
 */
@RunWith(JUnit4.class)
public class EnvoyServerProtoDataTest {

  @Test
  public void listener_convertFromListenerProto() throws InvalidProtocolBufferException {
    Address address =
        Address.newBuilder()
            .setSocketAddress(
                SocketAddress.newBuilder().setPortValue(8000).setAddress("10.2.1.34").build())
            .build();
    io.envoyproxy.envoy.config.listener.v3.Listener listener =
        io.envoyproxy.envoy.config.listener.v3.Listener.newBuilder()
            .setName("8000")
            .setAddress(address)
            .addFilterChains(createInFilter())
            .setDefaultFilterChain(createDefaultFilterChain())
            .setTrafficDirection(TrafficDirection.INBOUND)
            .build();

    Listener xdsListener = Listener.fromEnvoyProtoListener(listener, mock(TlsContextManager.class));
    assertThat(xdsListener.getName()).isEqualTo("8000");
    assertThat(xdsListener.getAddress()).isEqualTo("10.2.1.34:8000");
    List<EnvoyServerProtoData.FilterChain> filterChains = xdsListener.getFilterChains();
    assertThat(filterChains).isNotNull();
    assertThat(filterChains.size()).isEqualTo(1);

    EnvoyServerProtoData.FilterChain inFilter = filterChains.get(0);
    assertThat(inFilter).isNotNull();
    EnvoyServerProtoData.FilterChainMatch inFilterChainMatch = inFilter.getFilterChainMatch();
    assertThat(inFilterChainMatch).isNotNull();
    assertThat(inFilterChainMatch.getDestinationPort()).isEqualTo(8000);
    assertThat(inFilterChainMatch.getApplicationProtocols())
        .containsExactlyElementsIn(Arrays.asList("managed-mtls", "h2"));
    assertThat(inFilterChainMatch.getServerNames())
        .containsExactlyElementsIn(Arrays.asList("server1", "server2"));
    assertThat(inFilterChainMatch.getTransportProtocol()).isEqualTo("tls");
    assertThat(inFilterChainMatch.getPrefixRanges())
        .containsExactly(new EnvoyServerProtoData.CidrRange("10.20.0.15", 32));
    assertThat(inFilterChainMatch.getSourcePrefixRanges())
        .containsExactly(new EnvoyServerProtoData.CidrRange("10.30.3.0", 24));
    assertThat(inFilterChainMatch.getConnectionSourceType())
        .isEqualTo(EnvoyServerProtoData.ConnectionSourceType.EXTERNAL);
    assertThat(inFilterChainMatch.getSourcePorts()).containsExactly(200, 300);
    SslContextProviderSupplier sslContextProviderSupplier = inFilter
        .getSslContextProviderSupplier();
    assertThat(sslContextProviderSupplier.getTlsContext()).isInstanceOf(DownstreamTlsContext.class);
    DownstreamTlsContext inFilterTlsContext = (DownstreamTlsContext) sslContextProviderSupplier
        .getTlsContext();
    assertThat(inFilterTlsContext.getCommonTlsContext()).isNotNull();
    CommonTlsContext commonTlsContext = inFilterTlsContext.getCommonTlsContext();
    List<SdsSecretConfig> tlsCertSdsConfigs = commonTlsContext
        .getTlsCertificateSdsSecretConfigsList();
    assertThat(tlsCertSdsConfigs).hasSize(1);
    assertThat(tlsCertSdsConfigs.get(0).getName()).isEqualTo("google-sds-config-default");

    EnvoyServerProtoData.FilterChain defaultFilter = xdsListener.getDefaultFilterChain();
    assertThat(defaultFilter).isNotNull();
    EnvoyServerProtoData.FilterChainMatch defaultFilterChainMatch =
        defaultFilter.getFilterChainMatch();
    assertThat(defaultFilterChainMatch).isNotNull();
    assertThat(defaultFilterChainMatch.getDestinationPort()).isEqualTo(8001);
    assertThat(defaultFilterChainMatch.getPrefixRanges())
        .containsExactly(new EnvoyServerProtoData.CidrRange("10.20.0.16", 30));
  }

  private static FilterChain createInFilter() {
    FilterChain filterChain =
        FilterChain.newBuilder()
            .setFilterChainMatch(
                FilterChainMatch.newBuilder()
                    .setDestinationPort(UInt32Value.of(8000))
                    .addAllServerNames(Arrays.asList("server1", "server2"))
                    .setTransportProtocol("tls")
                    .addAllApplicationProtocols(Arrays.asList("managed-mtls", "h2"))
                    .addPrefixRanges(CidrRange.newBuilder()
                        .setAddressPrefix("10.20.0.15")
                        .setPrefixLen(UInt32Value.of(32))
                        .build())
                    .addSourcePrefixRanges(
                        CidrRange.newBuilder()
                            .setAddressPrefix("10.30.3.0")
                            .setPrefixLen(UInt32Value.of(24))
                            .build())
                    .setSourceType(FilterChainMatch.ConnectionSourceType.EXTERNAL)
                    .addSourcePorts(200)
                    .addSourcePorts(300)
                    .build())
            .setTransportSocket(TransportSocket.newBuilder().setName("envoy.transport_sockets.tls")
                .setTypedConfig(
                    Any.pack(CommonTlsContextTestsUtil.buildTestDownstreamTlsContext(
                        "google-sds-config-default", "ROOTCA")))
                .build())
            .addFilters(Filter.newBuilder()
                .setName("envoy.http_connection_manager")
                .setTypedConfig(Any.newBuilder()
                    .setTypeUrl(
                        "type.googleapis.com/"
                            + "envoy.extensions.filters.network.http_connection_manager"
                            + ".v3.HttpConnectionManager"))
                .build())
            .build();
    return filterChain;
  }

  private static FilterChain createDefaultFilterChain() {
    FilterChain filterChain =
        FilterChain.newBuilder()
            .setFilterChainMatch(
                FilterChainMatch.newBuilder()
                    .setDestinationPort(UInt32Value.of(8001))
                    .addPrefixRanges(
                        CidrRange.newBuilder()
                            .setAddressPrefix("10.20.0.16")
                            .setPrefixLen(UInt32Value.of(30))
                            .build())
                    .build())
            .setTransportSocket(
                TransportSocket.newBuilder()
                    .setName("envoy.transport_sockets.tls")
                    .setTypedConfig(
                        Any.pack(
                            CommonTlsContextTestsUtil.buildTestDownstreamTlsContext(
                                "google-sds-config-default", "ROOTCA")))
                    .build())
            .addFilters(
                Filter.newBuilder()
                    .setName("envoy.http_connection_manager")
                    .setTypedConfig(
                        Any.newBuilder()
                            .setTypeUrl(
                                "type.googleapis.com/"
                                    + "envoy.extensions.filters.network.http_connection_manager"
                                    + ".v3.HttpConnectionManager"))
                    .build())
            .build();
    return filterChain;
  }
}
