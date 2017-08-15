package com.meltmedia.jgroups.aws;

import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.stack.IpAddress;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Stream.empty;
import static java.util.stream.Stream.of;

public class IPAddressUtils {
  private static Log log = LogFactory.getLog(AWS_PING.class);

  private final int portNumber;
  private final int portRange;

  public IPAddressUtils(int portNumber, int portRange) {
    this.portNumber = portNumber;
    this.portRange = portRange;
  }

  public List<IpAddress> expandClusterMemberPorts(final List<String> privateIpAddresses) {
    return privateIpAddresses.stream()
        .flatMap(address -> IntStream.range(portNumber, portNumber + portRange + 1)
            .mapToObj(port -> new AddressAndPort(address, port)))
        .flatMap(addressAndPort -> {
          try {
            return of(new IpAddress(addressAndPort.address, addressAndPort.port));
          } catch (Exception e) {
            log.warn("failed to create ip address", e);
            return empty();
          }
        })
        .collect(Collectors.toList());
  }

  private static class AddressAndPort {
    public final String address;
    public final int port;

    private AddressAndPort(final String address, final int port) {
      this.address = address;
      this.port = port;
    }
  }
}
