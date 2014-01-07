package com.almende.dialog.adapter;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.util.ServerUtils;


public class MailServlet extends TextServlet {
	
    public static final String CC_ADDRESS_LIST_KEY = "cc_email";
    public static final String BCC_ADDRESS_LIST_KEY = "bcc_email";
	private static final long serialVersionUID = 6892283600126803780L;
	private static final String servletPath = "/_ah/mail/";
	private static final String adapterType = "MAIL";
	
	public void doErrorPost(HttpServletRequest req, HttpServletResponse res) {}
	
	@Override
	protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		
		Properties props = new Properties();
		javax.mail.Session mailSession = javax.mail.Session.getDefaultInstance(props, null);
		
		MimeMessage message = new MimeMessage(mailSession, req.getInputStream());
		
		String uri = req.getRequestURI();
		String recipient = uri.substring(servletPath.length());
		
		/*Address[] recipients = message.getAllRecipients();
		Address recipient=null;
		if (recipients.length>0){
			recipient = recipients[0];
		}*/ 
		return receiveMessage( message, recipient );
	}

        /** method separated from the original @link{MailServlet#receiveMessage(HttpServletRequest, HttpServletResponse)}
         * so that it can be tested without any data mock-ups.
         * @since  3/09/2013
         */
        private TextMessage receiveMessage( MimeMessage message, String recipient )
        throws Exception
        {
            TextMessage msg = new TextMessage();
            msg.setSubject("RE: "+message.getSubject());
            if (recipient != null && !recipient.equals("")){
    			msg.setLocalAddress(recipient.toString());
    		} else {
    			
    			Address[] recipients = message.getAllRecipients();
    			if (recipients.length>0){
    				InternetAddress recip= (InternetAddress)recipients[0];
    				msg.setLocalAddress(recip.getAddress());
    			} else
    				throw new Exception("MailServlet: Can't determine local address! (Dev)");
    			
    		}
    		
    		Address[] senders = message.getFrom();
    		if(senders != null && senders.length>0) {
    			InternetAddress sender = (InternetAddress) senders[0];
    			msg.setAddress(sender.getAddress());
    			msg.setRecipientName(sender.getPersonal());
    		}
    		
            Multipart mp = null;
            if(message.getContent() instanceof Multipart)
            {
                mp = (Multipart) message.getContent();
            }
            else 
            {
                mp = new MimeMultipart();
                mp.addBodyPart( new MimeBodyPart(new InternetHeaders(), message.getContent().toString().getBytes()) ); 
            }
            if ( mp.getCount() > 0 )
            {
                //trim old messages when a message is revieved via the reply button
//                    String body = trimOldReplies( mp.getBodyPart( 0 ).getContent().toString(), msg.getLocalAddress() );
                msg.setBody( mp.getBodyPart( 0 ).getContent().toString() );
                log.info( "Receive mail: " + msg.getBody() );
            }
    		return msg;
        }

//        private String trimOldReplies( String body, String localAddress )
//        {
//            String result = "";
//            ArrayList<String> nonNullMessageLines = new ArrayList<String>();
//            String[] bodyLines = body.split( "\n" );
//            for ( String bodyLine : bodyLines )
//            {
//                if(!bodyLine.trim().isEmpty())
//                {
//                    nonNullMessageLines.add( bodyLine.trim() );
//                }
//            }
//            boolean foundReplyHeader = false;
//            //parse the nonNullMessage lines
//            for ( String nonNullMessageLine : nonNullMessageLines )
//            {
//                if(foundReplyHeader && nonNullMessageLine.contains( ">" ))
//                {
//                    break;
//                }
//                else if( !nonNullMessageLine.contains( "<"+localAddress+">" ))
//                {
//                    result += nonNullMessageLine;
//                }
//                else
//                {
//                    foundReplyHeader = true;
//                }
//            }
//            return result;
//        }

        @Deprecated
        /**
         * @Deprecated use broadcastMessage instead
         */
	@Override
	protected int sendMessage(String message, String subject, String from, String fromName,
			String to, String toName, Map<String, Object> extras, AdapterConfig config) {
		Properties props = new Properties();
        javax.mail.Session session = javax.mail.Session.getDefaultInstance(props, null);

		try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(from));
            if(fromName!=null)
                msg.setFrom(new InternetAddress(from, fromName));
            msg.addRecipient(Message.RecipientType.TO,
                             new InternetAddress(to, toName));
            msg.setSubject(subject);
            msg.setContent( message, "text/html; charset=utf-8" );
            Transport.send(msg);
            
            String logString = String.format( "Email sent:\n" + "From: %s<%s>\n" + "To: %s<%s>\n" + "Subject: %s\n" + "Body: %s",
                                              fromName, from, toName, to, subject, message );
            if(ServerUtils.isInUnitTestingEnvironment())
            {
                //TODO: fix TestFramework dependency (Maven doesn't include it during normal builds)
//                TestFramework.log( msg );
            }
            log.info( logString );
            log.info("Send reply to mail post: "+(new Date().getTime()));

        } catch (AddressException e) {
            log.warning("Failed to send message, because wrong address: "+e.getLocalizedMessage());
        } catch (MessagingException e) {
        	log.warning("Failed to send message, because message: "+e.getLocalizedMessage());
        } catch (UnsupportedEncodingException e) {
        	log.warning("Failed to send message, because encoding: "+e.getLocalizedMessage());
		}
		return 1;		
	}
	
    @Override
    protected int broadcastMessage( String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config )
    {
//        final String userName = config.getXsiUser();
//        final String pass = config.getXsiPasswd();
//        Authenticator authenticator = null;
        Properties properties = new Properties();
//        log.info( "user: " + userName + " " + pass.length() );
//        if ( userName != null && !userName.isEmpty() && pass != null && !pass.isEmpty() )
//        {
//            properties.put( "mail.smtp.auth", true );
//            authenticator = new Authenticator()
//            {
//                private PasswordAuthentication pa = new PasswordAuthentication( userName, pass );
//
//                @Override
//                public PasswordAuthentication getPasswordAuthentication()
//                {
//                    return pa;
//                }
//            };
//        }
        Session session = Session.getDefaultInstance( properties, null );
        try
        {
            Message msg = new MimeMessage( session );
            if(senderName!=null)
            {
                msg.setFrom( new InternetAddress( from, senderName ) );
                //add the senderName to the reply list if its an emailId
                if ( senderName.contains( "@" ) )
                {
                    Address[] addresses = new InternetAddress[1];
                    addresses[0] = new InternetAddress( senderName );
                    msg.setReplyTo( addresses );
                }
            }
            else
            {
                msg.setFrom( new InternetAddress( from ) );
            }
            //add to list
            for ( String address : addressNameMap.keySet() )
            {
                String toName = addressNameMap.get( address ) != null ? addressNameMap.get( address ) : address;
                msg.addRecipient( Message.RecipientType.TO, new InternetAddress( address, toName ) );
            }
            //add cc list
            if ( extras.get( CC_ADDRESS_LIST_KEY ) != null )
            {
                if(extras.get( CC_ADDRESS_LIST_KEY ) instanceof Map)
                {
                    @SuppressWarnings("unchecked")
					Map<String, String> ccAddressNameMap = (Map<String, String>)extras.get( CC_ADDRESS_LIST_KEY );
                    for ( String address : ccAddressNameMap.keySet() )
                    {
                        String toName = ccAddressNameMap.get( address ) != null ? ccAddressNameMap.get( address ) : address;
                        msg.addRecipient( Message.RecipientType.CC, new InternetAddress( address, toName ) );
                    }
                }
                else
                {
                    log.severe( String.format( "CC list seen but not of Map type: %s",
                        ServerUtils.serializeWithoutException( extras.get( CC_ADDRESS_LIST_KEY ) ) ) );
                }
            }
            //add bcc list
            if ( extras.get( BCC_ADDRESS_LIST_KEY ) != null )
            {
                if(extras.get( BCC_ADDRESS_LIST_KEY ) instanceof Map)
                {
                    @SuppressWarnings("unchecked")
					Map<String, String> bccAddressNameMap = (Map<String, String>)extras.get( BCC_ADDRESS_LIST_KEY );
                    for ( String address : bccAddressNameMap.keySet() )
                    {
                        String toName = bccAddressNameMap.get( address ) != null ? bccAddressNameMap.get( address ) : address;
                        msg.addRecipient( Message.RecipientType.BCC, new InternetAddress( address, toName ) );
                    }
                }
                else
                {
                    log.severe( String.format( "BCC list seen but not of Map type: %s",
                        ServerUtils.serializeWithoutException( extras.get( BCC_ADDRESS_LIST_KEY ) ) ) );
                }
            }
            msg.setSubject( subject );
            msg.setContent( message, "text/html; charset=utf-8" );
            if(ServerUtils.isInUnitTestingEnvironment())
            {
                //TODO: fix TestFramework dependency (Maven doesn't include it during normal builds)
//                TestFramework.log( msg );
            }
            Transport.send( msg );

            log.info( "Send reply to mail post: " + ( new Date().getTime() ) );
        }
        catch ( MessagingException e )
        {
            log.warning( "Failed to send message, because message: " + e.getLocalizedMessage() );
        }
        catch ( UnsupportedEncodingException e )
        {
            log.warning( "Failed to send message, because encoding: " + e.getLocalizedMessage() );
        }
        return 1;
    }

	@Override
	protected String getServletPath() {
		return servletPath;
	}
	
	@Override
	protected String getAdapterType() {
		return adapterType;
	}
}
