package com.almende.dialog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.ContentDisposition;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.ws.rs.core.MediaType;

import org.junit.After;
import org.junit.Before;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.test.TestServlet;
import com.almende.dialog.util.ServerUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.io.ByteStreams;
import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletRunner;
import com.meterware.servletunit.ServletUnitClient;
import com.thetransactioncompany.cors.HTTPMethod;

public class TestFramework
{
    private final LocalServiceTestHelper helper = new LocalServiceTestHelper( new LocalDatastoreServiceTestConfig() );
    protected static final String localAddressMail = "info@dialog-handler.appspotmail.com";
    protected static final String localAddressChat = "info@dialog-handler.appspotchat.com";
    protected static final String remoteAddress = "sshetty@ask-cs.com";
    
    public static ThreadLocal<ServletRunner> servletRunner = new ThreadLocal<ServletRunner>();
    
    @Before
    public void setup()
    {
        helper.setUp();
        if(servletRunner.get() == null)
        {
            servletRunner.set( setupTestServlet() );
        }
    }
    
    @After
    public void tearDown()
    {
        helper.tearDown();
    }
    
    public static String fetchResponse( HTTPMethod httpMethods, String url, String payload )
    {
        ServletUnitClient newClient = servletRunner.get().newClient();
        WebRequest request = null;
        String result = "";
        switch ( httpMethods )
        {
            case GET:
                request = new GetMethodWebRequest( url );
                break;
            case POST:
                request = new PostMethodWebRequest( url, payload != null ? new ByteArrayInputStream( payload.getBytes() ) : null, 
                                                    MediaType.APPLICATION_JSON );
                break;
            default:
                break;
        }
        try
        {
            WebResponse response = newClient.getResponse( request );
            result = response.getText();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
        return result;
    }
    
    public Session getOrCreateSession(AdapterConfig adapterConfig, Collection<String> responders) 
    throws Exception
    {
        String sessionKey = adapterConfig.getAdapterType() + "|" + adapterConfig.getMyAddress() + "|" + new ObjectMapper().writeValueAsString( responders );
        Session session = Session.getSession( sessionKey, adapterConfig.getKeyword() );
        return session;
    }
    
    public static AdapterConfig createAdapterConfig( String adapterType, String publicKey, String myAddress, String initiatAgentURL) 
    throws Exception
    {
        AdapterConfig adapterConfig = new AdapterConfig();
        adapterConfig.setAdapterType( adapterType );
        adapterConfig.setAnonymous( false );
        adapterConfig.setPublicKey( publicKey );
        adapterConfig.setMyAddress( myAddress );
        adapterConfig.setAccessToken( "2630|Ask54de" );
        adapterConfig.setInitialAgentURL( initiatAgentURL );
        String adapterConfigString = adapterConfig.createConfig( ServerUtils.serialize( adapterConfig )).getEntity().toString();
        return ServerUtils.deserialize( adapterConfigString, AdapterConfig.class);
    }
    
    public static Method fetchMethodByReflection( String methodName, Class<?> class1, Class<?> parameterType )
    throws Exception
    {
        Collection<Class<?>> argumentList = new ArrayList< Class<?>>();
        argumentList.add( parameterType );
        return fetchMethodByReflection( methodName, class1, argumentList );
    }
    
    public static Method fetchMethodByReflection(String methodName, Class<?> class1, Collection<Class<?>> parameterTypes ) 
    throws Exception
    {
        Method declaredMethod = class1.getDeclaredMethod( methodName, parameterTypes.toArray( new Class[parameterTypes.size()] ) );
        declaredMethod.setAccessible( true );
        return declaredMethod;
    }
    
    public static Object invokeMethodByReflection( Method methodToBeFetched, Object targetObject, Object argObjects ) 
    throws Exception
    {
        Collection<Object> argumentValues = new ArrayList<Object>();
        argumentValues.add( argObjects );
        return invokeMethodByReflection( methodToBeFetched, targetObject, argumentValues );
    }
    
    public static Object invokeMethodByReflection( Method methodToBeFetched, Object targetObject, 
                    Collection<Object> argObjects ) throws Exception
    {
        return methodToBeFetched.invoke( targetObject, argObjects.toArray( new Object[argObjects.size()] ));
    }
    
    public static Message getTestXMPPMessage(String body) throws Exception
    {
        MessageBuilder builder = new MessageBuilder();
        builder.withMessageType( MessageType.CHAT );
        MimeMultipart multipart = getTestMimeMultipart( remoteAddress, localAddressChat, body, null);
        int parts = multipart.getCount();
        for ( int i = 0; i < parts; i++ )
        {
            BodyPart part = multipart.getBodyPart( i );
            String fieldName = getFieldName( part );
            if ( "from".equals( fieldName ) )
            {
                builder.withFromJid( new JID( getTextContent( part ) ) );
            }
            else if ( "to".equals( fieldName ) )
            {
                builder.withRecipientJids( new JID( getTextContent( part ) ) );
            }
            else if ( "body".equals( fieldName ) )
            {
                builder.withBody( getTextContent( part ) );
            }
            else if ( "stanza".equals( fieldName ) )
            {
                Method withStanzaMethod = fetchMethodByReflection( "withStanza", MessageBuilder.class, String.class );
                invokeMethodByReflection( withStanzaMethod, builder, getTextContent( part ) );
            }
        }

        return builder.build();
    }
    
    public static MimeMultipart getTestMimeMultipart(String from, String to, String body, String stanza) throws MessagingException
    {
        MimeMultipart mimeMultipart = new MimeMultipart();
        if(body != null)
        {
            addHeader( mimeMultipart, "body", body );
        }
        if(from != null)
        {
            addHeader( mimeMultipart, "from", from );
        }
        if(to != null)
        {
            addHeader( mimeMultipart, "to", to );
        }
        if(stanza != null)
        {
            addHeader( mimeMultipart, "stanza", stanza );
        }
        return mimeMultipart;
    }
    
    private static void addHeader(MimeMultipart mimeMultipart, String key, String value) throws MessagingException
    {
        //value appended to the headerValue having from, to, body etc
        String headerValue = "form-data; name=";
        InternetHeaders internetHeaders = new InternetHeaders( new ByteArrayInputStream( "Content-Disposition".getBytes() ));
        internetHeaders.removeHeader( "Content-Disposition" );
        String appendedHeaderValue = headerValue + "\"" + key + "\"" ; 
        internetHeaders.addHeader( "Content-Disposition", appendedHeaderValue );
        mimeMultipart.addBodyPart( new MimeBodyPart( internetHeaders, value.getBytes()) );
    }
    
    private static String getFieldName( BodyPart part ) throws MessagingException
    {
        String[] values = part.getHeader( "Content-Disposition" );
        String name = null;
        if ( values != null && values.length > 0 )
        {
            name = new ContentDisposition( values[0] ).getParameter( "name" );
        }
        return ( name != null ) ? name : "unknown";
    }
    
    private static String getTextContent( BodyPart part ) throws MessagingException, IOException
    {
        ContentType contentType = new ContentType( part.getContentType() );
        String charset = contentType.getParameter( "charset" );
        if ( charset == null )
        {
            charset = "ISO-8859-1";
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteStreams.copy( part.getInputStream(), baos );
        try
        {
            return new String( baos.toByteArray(), charset );
        }
        catch ( UnsupportedEncodingException ex )
        {
            return new String( baos.toByteArray() );
        }
    }
    
    private ServletRunner setupTestServlet()
    {
        ServletRunner servletRunner = new ServletRunner();
        servletRunner.registerServlet( "unitTestServlet", TestServlet.class.getName() );
        return servletRunner;
    }
}