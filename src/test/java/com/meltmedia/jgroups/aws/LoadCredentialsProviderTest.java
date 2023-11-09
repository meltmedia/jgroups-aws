package com.meltmedia.jgroups.aws;

import org.jgroups.logging.Log;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.identity.spi.ResolveIdentityRequest;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * Basic tests for loading credentials provider classes from different contexts.
 * 
 * @author Christian Trimble
 */
public class LoadCredentialsProviderTest {

  @SuppressWarnings("unchecked")
  @Test
  public void credentialProviderNotFound() throws Exception {
    Log log = Mockito.mock(Log.class);

    ClassLoader contextClassLoader = Mockito.mock(ClassLoader.class, answerWith(Thread.currentThread().getContextClassLoader()));
    when((Class<UnsupportedAWSCredentialProvider>)contextClassLoader.loadClass(UnsupportedAWSCredentialProvider.class.getName()))
      .thenReturn(UnsupportedAWSCredentialProvider.class);

    try {
      doCall(contextClassLoader, "com.meltmedia.jgroups.aws.NotFoundCredentialProvider", log);
      fail("load credential provider succeeded for an undefined class.");
    }
    catch( Exception e ) {
      verify(contextClassLoader).loadClass("com.meltmedia.jgroups.aws.NotFoundCredentialProvider");
    }
  }

  @SuppressWarnings("unchecked")
//  @Test  //  jgroups.Util.loadClass prefers jgroupsClass.getClassLoader()
  public void contextClassLoaderSearchedFirst() throws Exception {
    Log log = Mockito.mock(Log.class);

    ClassLoader contextClassLoader = Mockito.mock(ClassLoader.class, answerWith(Thread.currentThread().getContextClassLoader()));
    when((Class<UnsupportedAWSCredentialProvider>)contextClassLoader.loadClass(UnsupportedAWSCredentialProvider.class.getName()))
      .thenReturn(UnsupportedAWSCredentialProvider.class);

    doCall(contextClassLoader, UnsupportedAWSCredentialProvider.class.getName(), log);
    verify(contextClassLoader).loadClass(UnsupportedAWSCredentialProvider.class.getName());
  }

  @SuppressWarnings("unchecked")
//  @Test // Same as above.
  public void exceptionOnMissingNoArgConstructor() throws Exception {
    Log log = Mockito.mock(Log.class);

    ClassLoader contextClassLoader = Mockito.mock(ClassLoader.class, answerWith(Thread.currentThread().getContextClassLoader()));
    when((Class<BadConstructorAWSCredentialsProvider>)contextClassLoader.loadClass(BadConstructorAWSCredentialsProvider.class.getName()))
      .thenReturn(BadConstructorAWSCredentialsProvider.class);

    try {
      doCall(contextClassLoader, BadConstructorAWSCredentialsProvider.class.getName(), log);
      fail("This credential provider should not have been constructed.");
    }
    catch( InstantiationException ie ) {
      // make sure that we notified the log.
      verify(contextClassLoader).loadClass(BadConstructorAWSCredentialsProvider.class.getName());
      verify(log).error(anyString());
    }
  }

  @Test 
  public void noContextClassLoader() throws Exception {
    Log log = Mockito.mock(Log.class);
    
    doCall(null, DefaultCredentialsProvider.class.getName(), log);
  }
  
  public static Answer<Object> answerWith(final Object o) {
    return new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        return invocation.getMethod().invoke(o, invocation.getArguments());
      }
    };
  }
  
  /**
   * A credentials provider with no real implementation.
   */
  public static class UnsupportedAWSCredentialProvider implements AwsCredentialsProvider {
    @Override
    public AwsCredentials resolveCredentials() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Class<AwsCredentialsIdentity> identityType() {
      return AwsCredentialsProvider.super.identityType();
    }

    @Override
    public CompletableFuture<AwsCredentialsIdentity> resolveIdentity(ResolveIdentityRequest request) {
      return AwsCredentialsProvider.super.resolveIdentity(request);
    }

    public static UnsupportedAWSCredentialProvider create() {
      return new UnsupportedAWSCredentialProvider();
    }
  }
  
  /**
   * A credentials provider that does not supply the needed constructor.
   */
  public static class BadConstructorAWSCredentialsProvider extends UnsupportedAWSCredentialProvider {

  }
  
  public static AwsCredentialsProvider doCall( ClassLoader contextClassLoader, String className, Log log) throws Exception
  {
    ClassLoader oldCtx = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(contextClassLoader);
      return new CredentialsProviderFactory(log).createCredentialsProvider(className);
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldCtx);
    }
  }

}
