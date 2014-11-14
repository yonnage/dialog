package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.TwilioAdapter.Return;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.askfast.commons.utils.PhoneNumberUtils;

public class TwilioAdapterTest extends TestFramework {
	
	Logger log = Logger.getLogger(TwilioAdapterTest.class.getName());
	
	protected static final String COMMENT_QUESTION_ID = "1";
    protected static final String COMMENT_QUESTION_AUDIO = "http://audio";
    protected static final String COMMENT_QUESTION_TEXT = "text://Hello World";
    protected static final String REFERRAL_PHONE_NUMBER = "tel:0643002549";
    
    @Test
    public void renderCommentQuestionTest() throws Exception {
        
        Question question = getCommentQuestion(false);
        AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node redirect = play.getNextSibling();
        
        
        assertEquals("Play", play.getNodeName());
        assertEquals("http://audio", play.getTextContent());
        assertEquals("Redirect", redirect.getNodeName());
        assertTrue(redirect.getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        
        assertEquals("GET", redirect.getAttributes().getNamedItem("method").getTextContent());
    }
    
    @Test
    public void renderCommentQuestionTTSTest() throws Exception {
        
        Question question = getCommentQuestion(true);
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
                
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node say = response.getFirstChild();
        Node redirect = say.getNextSibling();
        
        
        assertEquals("Say", say.getNodeName());
        assertEquals("Hello World", say.getTextContent());
        assertEquals("nl-NL", say.getAttributes().getNamedItem("language").getTextContent());
        assertEquals("Redirect", redirect.getNodeName());
        assertTrue(redirect.getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        
        assertEquals("GET", redirect.getAttributes().getNamedItem("method").getTextContent());
    }
	
	@Test
	public void renderOpenQuestionTest() throws Exception {
		
		Question question = getOpenQuestion(false, false);
		AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        log.info("Result Open Question: "+result);
        
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node gather = response.getFirstChild();
        Node play = gather.getFirstChild();
        Node redirect = gather.getNextSibling();
        
        assertEquals("Gather", gather.getNodeName());
        assertEquals("GET", gather.getAttributes().getNamedItem("method").getTextContent());
        assertTrue(gather.getAttributes().getNamedItem("action").getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        assertEquals("Play", play.getNodeName());
        assertEquals("http://audio", play.getTextContent());
        assertEquals("Redirect", redirect.getNodeName());
        assertTrue(redirect.getTextContent().endsWith("/dialoghandler/rest/twilio/timeout"));
        
        assertEquals("GET", redirect.getAttributes().getNamedItem("method").getTextContent());
	}
	
	@Test
	public void renderOpenAudioQuestionTest() throws Exception {
		
		Question question = getOpenQuestion(false, true);
		AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        log.info("Result Open Question: "+result);
        
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node record = play.getNextSibling();
        Node redirect = record.getNextSibling();
        
        assertEquals("Play", play.getNodeName());
        assertEquals("http://audio", play.getTextContent());
        
        assertEquals("Record", record.getNodeName());
        assertEquals("GET", record.getAttributes().getNamedItem("method").getTextContent());
        assertTrue(record.getAttributes().getNamedItem("action").getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        
        assertEquals("Redirect", redirect.getNodeName());
        assertTrue(redirect.getTextContent().endsWith("/dialoghandler/rest/twilio/timeout"));
        
        assertEquals("GET", redirect.getAttributes().getNamedItem("method").getTextContent());
	}
	
	@Test
	public void renderClosedQuestionTest() throws Exception {
		
		Question question = getClosedQuestion(false);
		AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
                
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node gather = response.getFirstChild();
        Node play1 = gather.getFirstChild();
        Node play2 = play1.getNextSibling();
        Node play3 = play2.getNextSibling();
        Node redirect = gather.getNextSibling();
        
        assertEquals("Gather", gather.getNodeName());
        assertEquals("GET", gather.getAttributes().getNamedItem("method").getTextContent());
        assertTrue(gather.getAttributes().getNamedItem("action").getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        assertEquals("Play", play1.getNodeName());
        assertEquals("http://audio", play1.getTextContent());
        assertEquals("Play", play2.getNodeName());
        assertEquals("http://answer1.wav", play2.getTextContent());
        assertEquals("Play", play3.getNodeName());
        assertEquals("http://answer2.wav", play3.getTextContent());
        assertEquals("Redirect", redirect.getNodeName());
        assertTrue(redirect.getTextContent().endsWith("/dialoghandler/rest/twilio/timeout"));
        
        assertEquals("GET", redirect.getAttributes().getNamedItem("method").getTextContent());
	}
	
	@Test
	public void renderReferralQuestionTest() throws Exception {
		
		Question question = getReferralQuestion(false, false);
		AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        

        log.info("Result Referral Question: "+result);
        
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node dial = play.getNextSibling();
        Node number = dial.getFirstChild();
        
        assertEquals("Play", play.getNodeName());
        assertEquals("http://audio", play.getTextContent());
        
        assertEquals("Dial", dial.getNodeName());
        assertEquals("GET", dial.getAttributes().getNamedItem("method").getTextContent());
        assertTrue(dial.getAttributes().getNamedItem("action").getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        assertEquals(localAddressBroadsoft, dial.getAttributes().getNamedItem("callerId").getTextContent());
     
        String formattedAddress = PhoneNumberUtils.formatNumber(remoteAddressVoice, null);
        assertEquals("Number", number.getNodeName());
        assertEquals(formattedAddress, number.getTextContent());
	}
	
	@Test
	public void renderReferralQuestionExternalCIDTest() throws Exception {
		
		Question question = getReferralQuestion(true, false);
		AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        

        log.info("Result Referral Question: "+result);
        
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node dial = play.getNextSibling();
        Node number = dial.getFirstChild();
        
        assertEquals("Play", play.getNodeName());
        assertEquals("http://audio", play.getTextContent());
        
        assertEquals("Dial", dial.getNodeName());
        assertEquals("GET", dial.getAttributes().getNamedItem("method").getTextContent());
        assertTrue(dial.getAttributes().getNamedItem("action").getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        assertEquals(remoteAddressVoice, dial.getAttributes().getNamedItem("callerId").getTextContent());
     
        String formattedAddress = PhoneNumberUtils.formatNumber(remoteAddressVoice, null);
        assertEquals("Number", number.getNodeName());
        assertEquals(formattedAddress, number.getTextContent());
	}
	
	@Test
	public void renderReferralPreconnectTest() throws Exception {
		
		Question question = getReferralQuestion(true, true);
		AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        

        log.info("Result Referral Question: "+result);
        
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node dial = play.getNextSibling();
        Node number = dial.getFirstChild();
        
        assertEquals("Play", play.getNodeName());
        assertEquals("http://audio", play.getTextContent());
        
        assertEquals("Dial", dial.getNodeName());
        assertEquals("GET", dial.getAttributes().getNamedItem("method").getTextContent());
        assertTrue(dial.getAttributes().getNamedItem("action").getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        assertEquals(remoteAddressVoice, dial.getAttributes().getNamedItem("callerId").getTextContent());
     
        String formattedAddress = PhoneNumberUtils.formatNumber(remoteAddressVoice, null);
        assertEquals("Number", number.getNodeName());
        assertEquals(formattedAddress, number.getTextContent());
        assertTrue(number.getAttributes().getNamedItem("url").getTextContent().endsWith("/dialoghandler/rest/twilio/preconnect"));
	}
    
    private Question getCommentQuestion(boolean tts) {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType("comment");
        if(tts) {
        	question.setQuestion_text(COMMENT_QUESTION_TEXT);
        } else {
        	question.setQuestion_text(COMMENT_QUESTION_AUDIO);
        }

        Answer answer = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer)));

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private Question getOpenQuestion(boolean tts, boolean audio) {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType("open");
        if(tts) {
        	question.setQuestion_text(COMMENT_QUESTION_TEXT);
        } else {
        	question.setQuestion_text(COMMENT_QUESTION_AUDIO);
        }

        Answer answer = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer)));
        
        if(audio) {
        	MediaProperty mp = new MediaProperty();
        	mp.setMedium(MediumType.BROADSOFT);
        	mp.addProperty(MediaPropertyKey.TYPE, "audio");
        	question.addMedia_Properties(mp);
        }

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private Question getClosedQuestion(boolean tts) {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType("closed");
        if(tts) {
        	question.setQuestion_text(COMMENT_QUESTION_TEXT);
        } else {
        	question.setQuestion_text(COMMENT_QUESTION_AUDIO);
        }

        Answer answer1 = new Answer("http://answer1.wav", "/next");
        Answer answer2 = new Answer("http://answer2.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer1, answer2)));

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private Question getReferralQuestion(boolean useExternalCallerId, boolean usePreconnect) {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType("referral");
        question.setQuestion_text(COMMENT_QUESTION_AUDIO);
        
        question.setUrl("tel:"+remoteAddressVoice);

        Answer answer1 = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer1)));
        
        MediaProperty mp = new MediaProperty();
        mp.setMedium(MediumType.BROADSOFT);
        
        if(useExternalCallerId) {
	    	mp.addProperty(MediaPropertyKey.USE_EXTERNAL_CALLERID, "true");
        }
        
        if(usePreconnect) {
	    	mp.addProperty(MediaPropertyKey.USE_PRECONNECT, "true");
        }
        
        question.addMedia_Properties(mp);

        // set the answers in the question
        question.generateIds();
        return question;
    }
	
	private String renderQuestion(Question question, AdapterConfig adapter, String sessionKey) throws Exception {

		TwilioAdapter servlet = new TwilioAdapter();
        Return res = servlet.formQuestion(question, adapter.getConfigId(), remoteAddressVoice, null, sessionKey);

        if (question.getType().equalsIgnoreCase("comment")) {
            return servlet.renderComment(res.question, res.prompts, sessionKey);
        }
        else if (question.getType().equalsIgnoreCase("referral")) {
        	
        	String remoteID = remoteAddressVoice;
        	String externalCallerId = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.USE_EXTERNAL_CALLERID );
            Boolean callerId = false;
            if(externalCallerId!=null) {
            	callerId = Boolean.parseBoolean(externalCallerId);
            }
    		if(!callerId) {
    			remoteID = adapter.getMyAddress();
    		}

        	String redirectedId = PhoneNumberUtils.formatNumber(question.getUrl().replace("tel:", ""), null);
			if (redirectedId != null) {
				// update url with formatted redirecteId. RFC3966
				// returns format tel:<blabla> as expected
				question.setUrl(redirectedId);
			}
			return servlet.renderReferral(res.question, res.prompts, sessionKey, remoteID);
        }
        else if (question.getType().equalsIgnoreCase("open")) {
            return servlet.renderOpenQuestion(res.question, res.prompts, sessionKey);
        }
        else if (question.getType().equalsIgnoreCase("closed")) {
        	return servlet.renderClosedQuestion(res.question, res.prompts, sessionKey);
        }

        return null;
    }
}