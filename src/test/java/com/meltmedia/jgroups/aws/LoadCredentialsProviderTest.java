package com.meltmedia.jgroups.aws;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.jgroups.logging.Log;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;

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
    
    doCall(null, com.amazonaws.auth.DefaultAWSCredentialsProviderChain.class.getName(), log);
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
  public static class UnsupportedAWSCredentialProvider implements AWSCredentialsProvider {
    @Override
    public AWSCredentials getCredentials() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void refresh() {
      throw new UnsupportedOperationException();
    }  
  }
  
  /**
   * A credentials provider that does not supply the needed constructor.
   */
  public static class BadConstructorAWSCredentialsProvider implements AWSCredentialsProvider {
    public BadConstructorAWSCredentialsProvider( String tooManyArgs) {}
    @Override
    public AWSCredentials getCredentials() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void refresh() {
      throw new UnsupportedOperationException();
    }  
  }
  
  public static AWSCredentialsProvider doCall( ClassLoader contextClassLoader, String className, Log log) throws Exception
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
