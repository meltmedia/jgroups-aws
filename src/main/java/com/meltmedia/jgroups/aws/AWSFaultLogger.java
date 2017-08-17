package com.meltmedia.jgroups.aws;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.Request;
import com.amazonaws.handlers.RequestHandler;
import com.amazonaws.transform.Unmarshaller;
import com.amazonaws.util.TimingInfo;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.w3c.dom.Node;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import java.io.StringWriter;

/**
 * This class will log the request along with the response from the AWS ec2 service on fault only.
 *
 * @author John McEntire
 */
@SuppressWarnings("deprecation")
class AWSFaultLogger implements Unmarshaller<AmazonServiceException, Node>, RequestHandler {
  private static Log log = LogFactory.getLog(AWS_PING.class);
  private final ThreadLocal<Request<?>> request = new ThreadLocal<>();

  @Override
  public AmazonServiceException unmarshall(Node node) throws Exception {
    try {
      final TransformerFactory tfactory = TransformerFactory.newInstance();
      final Transformer xform = tfactory.newTransformer();
      final Source src = new javax.xml.transform.dom.DOMSource(node);
      final StringWriter writer = new StringWriter();
      final Result result = new javax.xml.transform.stream.StreamResult(writer);

      xform.transform(src, result);
      log.error("AWS Exception: [%s] For request [%s]", writer, request.get());
    } catch (Throwable t) {
      log.debug("Failed to log xml soap fault message.", t);
    } finally {
      request.remove();
    }
    return null;
  }

  @Override
  public void afterError(Request<?> request, Exception e) {
    this.request.remove();
  }

  @Override
  public void afterResponse(Request<?> request, Object obj, TimingInfo timing) {
    this.request.remove();
  }

  @Override
  public void beforeRequest(Request<?> request) {
    this.request.set(request);
  }
}
