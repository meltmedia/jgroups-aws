package com.meltmedia.jgroups.aws;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.paginators.DescribeInstancesIterable;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Mocks {
  public static Ec2Client ec2Mock(Tag... tags) {
    final Ec2Client ec2 = mock(Ec2Client.class);

    final Reservation reservation = mock(Reservation.class);
    final Instance instance = mock(Instance.class);
    when(instance.tags()).thenReturn(Arrays.asList(tags));
    when(reservation.instances()).thenReturn(Collections.singletonList(instance));
    DescribeInstancesIterable paginator = mock(DescribeInstancesIterable.class);
    when(paginator.reservations()).thenReturn(() -> Collections.singletonList(reservation).iterator());
    when(ec2.describeInstancesPaginator(any(Consumer.class))).thenReturn(paginator);

    return ec2;
  }
}
