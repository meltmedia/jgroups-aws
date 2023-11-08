package com.meltmedia.jgroups.aws;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Mocks {
  public static AmazonEC2 ec2Mock(Tag... tags) {
    final AmazonEC2 ec2 = mock(AmazonEC2.class);

    final DescribeInstancesResult describeInstancesResult = mock(DescribeInstancesResult.class);
    final Reservation reservation = mock(Reservation.class);
    final Instance instance = mock(Instance.class);
    when(instance.getTags()).thenReturn(Arrays.asList(tags));
    when(reservation.getInstances()).thenReturn(Arrays.asList(instance));
    when(describeInstancesResult.getReservations()).thenReturn(Arrays.asList(reservation));
    when(ec2.describeInstances(any(DescribeInstancesRequest.class))).thenReturn(describeInstancesResult);

    return ec2;
  }
}
