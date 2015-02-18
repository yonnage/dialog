package com.almende.dialog.adapter;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.utils.PhoneNumberUtils;

@Category(IntegrationTest.class)
public class VoiceXMLServletIT extends TestFramework {

    protected static final String COMMENT_QUESTION_AUDIO = "http://audio.wav";
    private DialogAgent dialogAgent = null;
    
    /**
     * this test is to check the bug which rethrows the same question when an
     * open question doesnt have an answer nor a timeout eventtype
     * 
     * @throws Exception
     */
    @Test
    public void inboundPhoneCall_WithOpenQuestion_MissingAnswerTest() throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_BROADSOFT, TEST_PUBLIC_KEY,
                                                          localAddressBroadsoft, url);

        //create session
        Session.createSession(adapterConfig, remoteAddressVoice);

        //mock the Context
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        Response newDialog = voiceXMLRESTProxy.getNewDialog("inbound", remoteAddressVoice, remoteAddressVoice, localAddressBroadsoft,
                                                            uriInfo);
        HashMap<String, String> answerVariables = assertOpenQuestionWithDTMFType(newDialog.getEntity().toString());

        //answer the dialog
        Question retrivedQuestion = ServerUtils.deserialize(TestFramework.fetchResponse(HttpMethod.GET, url, null),
                                                            Question.class);
        String mediaPropertyValue = retrivedQuestion.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                           MediaPropertyKey.RETRY_LIMIT);

        Integer retryCount = Question.getRetryCount(answerVariables.get("sessionKey"));
        int i = 0;
        while (i++ < 10) {
            Response answerResponse = voiceXMLRESTProxy.answer(answerVariables.get("questionId"), null,
                                                               answerVariables.get("answerInput"),
                                                               answerVariables.get("sessionKey"), uriInfo);
            if (answerResponse.getEntity() != null) {
                if (answerResponse.getEntity()
                                                .toString()
                                                .equals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                                                                        + "<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">"
                                                                                        + "<form><block><exit/></block></form></vxml>")) {
                    break;
                }
            }
            retryCount++;
        }
        assertTrue(retryCount != null);
        if (mediaPropertyValue != null) {
            assertTrue(retryCount < i);
            assertTrue(retryCount == Integer.parseInt(mediaPropertyValue));
        }
        else {
            assertTrue(retryCount <= i);
            assertEquals(new Integer(Question.DEFAULT_MAX_QUESTION_LOAD), retryCount);
        }
    }
    
    /**
     * This test is used to simulate the situation when an outbound call is triggered, but the 
     * corresponding ddrRecord is missing from the session
     * @throws Exception 
     */
    @SuppressWarnings("deprecation")
    @Test
    public void outboundPhoneCallMissingDDRTest() throws Exception {
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.OPEN_QUESTION.name());
        
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_BROADSOFT, TEST_PUBLIC_KEY,
                                                          localAddressBroadsoft, url);
        adapterConfig.setXsiUser(localAddressBroadsoft + "@ask.ask.voipit.nl");
        adapterConfig.setXsiSubscription(TEST_PUBLIC_KEY);
        adapterConfig.update();

        //setup some ddrPrices
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.8, "Test outgoing", UnitType.SECOND, null, null);
        
        //trigger an outbound call
        VoiceXMLRESTProxy.dial(remoteAddressVoice, url, adapterConfig, adapterConfig.getOwner());
        //fetch the session, assert that a ddrRecord is not attached still
        Session session = Session.getSession(AdapterAgent.ADAPTER_TYPE_BROADSOFT, localAddressBroadsoft,
                                             PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        assertThat(session, notNullValue());
        assertThat(session.getDdrRecordId(), Matchers.notNullValue());
        
        List<DDRRecord> allDdrRecords = DDRRecord.getDDRRecords(null, null, null, null, null, null, null, null, null);
        assertThat(allDdrRecords.isEmpty(), Matchers.is(true));
        
        //mock the Context
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        //mimick a fetch new dialog/ phone pickup
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        Response newDialog = voiceXMLRESTProxy.getNewDialog("outbound", remoteAddressVoice, remoteAddressVoice, localAddressBroadsoft,
                                                            uriInfo);
        assertOpenQuestionWithDTMFType(newDialog.getEntity().toString());
        //a ddr must be attached to hte session
        session = Session.getSession(session.getKey());
        assertThat(session, Matchers.notNullValue());
        assertThat(session.getDdrRecordId(), Matchers.notNullValue());
        
        //hangup the call after 5 mins
        //send hangup ccxml with an answerTime
        String hangupXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Event xmlns=\"http://schema.broadsoft.com/xsi-events\" " +
                           "xmlns:xsi1=\"http://www.w3.org/2001/XMLSchema-instance\"><sequenceNumber>257</sequenceNumber><subscriberId>" +
                           localAddressBroadsoft +
                           "@ask.ask.voipit.nl</subscriberId>" +
                           "<applicationId>cc</applicationId><subscriptionId>" +
                           TEST_PUBLIC_KEY +
                           "</subscriptionId><eventData xsi1:type=\"xsi:CallEvent\" xmlns:xsi=" +
                           "\"http://schema.broadsoft.com/xsi-events\"><eventName>CallSessionEvent</eventName><call><callId>callhalf-12914560105:1</callId><extTrackingId>" +
                           "10669651:1</extTrackingId><personality>Originator</personality><callState>Released</callState><releaseCause>Temporarily Unavailable</releaseCause>" +
                           "<remoteParty><address>tel:" +
                           remoteAddressVoice +
                           "</address><callType>Network</callType></remoteParty><startTime>1401809063943</startTime>" +
                           "<answerTime>1401809070192</answerTime><releaseTime>1401809370000</releaseTime></call></eventData></Event>";
        voiceXMLRESTProxy.receiveCCMessage(hangupXML);
        //fetch the ddrRecord again
        DDRRecord ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(), session.getAccountId());
        ddrRecord.setShouldGenerateCosts(true);
        ddrRecord.setShouldIncludeServiceCosts(true);
        assertThat(ddrRecord, Matchers.notNullValue());
        assertThat(ddrRecord.getDuration(), Matchers.greaterThan(0L));
        assertThat(ddrRecord.getStart(), Matchers.is(1401809070192L));
    }
    
    /**
     * Performs an outbound call request with Broadsoft. Test it with a switch to Voxeo
     * and test if everything works normally 
     */
    @Test
    public void outboundWithGlobalSwitchOnTest() throws Exception {

        dialogAgent = Mockito.mock(DialogAgent.class);
        Mockito.when(dialogAgent.getGlobalSwitchProviderCredentials()).thenReturn(null);
        
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.OPEN_QUESTION.name());

        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_BROADSOFT, TEST_PUBLIC_KEY,
                                                          localAddressBroadsoft, url);
        adapterConfig.setXsiUser(localAddressBroadsoft + "@ask.ask.voipit.nl");
        adapterConfig.setXsiSubscription(TEST_PUBLIC_KEY);
        adapterConfig.update();

        //perform an outbound call
        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, "");
        Mockito.when(dialogAgent.outboundCallWithMap(addressMap, null, null, null, null, url, null,
                                                     adapterConfig.getConfigId(), adapterConfig.getOwner(), ""))
                                        .thenCallRealMethod();
        HashMap<String, String> result = dialogAgent.outboundCallWithMap(addressMap, null, null, null, null, url, null,
                                                                         adapterConfig.getConfigId(),
                                                                         adapterConfig.getOwner(), "");
        assertTrue(result != null);
        assertTrue(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)) != null);
        Session.drop(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        
        //set switch related test info
        String testMyAddress = "0854881001";
        
        Map<AdapterProviders, Map<String, Map<String, Object>>> globalAdapterCredentials = new HashMap<AdapterProviders, Map<String, Map<String, Object>>>();
        HashMap<String, Map<String, Object>> credentials = new HashMap<String, Map<String, Object>>();
        HashMap<String, Object> actualCredentials = new HashMap<String, Object>();
        actualCredentials.put("accessToken", "testTest");
        actualCredentials.put("myAddress", testMyAddress);
        actualCredentials.put("address", testMyAddress);
        actualCredentials.put("accessTokenSecret", "testTestSecret");
        actualCredentials.put("adapterType", "voxeo");
        credentials.put(DialogAgent.ADAPTER_CREDENTIALS_GLOBAL_KEY, actualCredentials);
        globalAdapterCredentials.put(AdapterProviders.TWILIO, credentials);
        
        //mock the Context
        Mockito.when(dialogAgent.getGlobalSwitchProviderCredentials()).thenReturn(globalAdapterCredentials);
        //switch calling adapter globally
        Mockito.when(dialogAgent.getGlobalAdapterSwitchSettings(AdapterType.CALL)).thenReturn(AdapterProviders.TWILIO);
        Mockito.when(dialogAgent.getApplicationId()).thenReturn(UUID.randomUUID().toString());
        
        //initiate outbound request again
        
        result = dialogAgent.outboundCallWithMap(addressMap, null, null, null, null, url, null,
                                                       adapterConfig.getConfigId(), adapterConfig.getOwner(), "");
        assertTrue(result != null);
        assertTrue(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)) != null);
        Session session = Session.getSession(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        assertTrue(session != null);
        assertEquals(testMyAddress, session.getLocalAddress());
        assertEquals(AdapterProviders.TWILIO.getName(), session.getType());
    }
    
    /**
     * Performs an outbound call request with Broadsoft. Test it with a switch
     * to Voxeo and test if everything works normally
     */
    @Test
    public void outboundWithSpecificAdapterSwitchOnTest() throws Exception {

        //perform a global switch test
        outboundWithGlobalSwitchOnTest();
        
        //fetch the session
        Session session = Session.getSession(AdapterAgent.ADAPTER_TYPE_TWILIO, "0854881001",
                                             PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        //add a specific adapter switch
        String testMyAddress = "0854881002";
        Map<AdapterProviders, Map<String, Map<String, Object>>> globalSwitchProviderCredentials = dialogAgent
                                        .getGlobalSwitchProviderCredentials();
        Map<String, Map<String, Object>> credentials = globalSwitchProviderCredentials.get(AdapterProviders.TWILIO);
        HashMap<String, Object> actualCredentials = new HashMap<String, Object>();
        actualCredentials.put("accessToken", "testTest");
        actualCredentials.put("myAddress", testMyAddress);
        actualCredentials.put("address", testMyAddress);
        actualCredentials.put("accessTokenSecret", "testTestSecret");
        actualCredentials.put("adapterType", "voxeo");
        credentials.put(session.getAdapterID(), actualCredentials);
        globalSwitchProviderCredentials.put(AdapterProviders.TWILIO, credentials);

        //mock the Context
        Mockito.when(dialogAgent.getGlobalSwitchProviderCredentials()).thenReturn(globalSwitchProviderCredentials);

        //initiate outbound request again
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.OPEN_QUESTION.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, "");
        HashMap<String, String> result = dialogAgent.outboundCallWithMap(addressMap, null, null, null, null, url, null,
                                                                         session.getAdapterID(),
                                                                         session.getAdapterConfig().getOwner(), "");
        assertTrue(result != null);
        assertTrue(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)) != null);
        session = Session.getSession(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        assertTrue(session != null);
        assertEquals(testMyAddress, session.getLocalAddress());
        assertEquals(AdapterProviders.TWILIO.getName(), session.getType());
    }
    
    /**
     * @param result
     * @throws Exception
     */
    protected HashMap<String, String> assertOpenQuestionWithDTMFType(String result) throws Exception {

        HashMap<String, String> variablesForAnswer = new HashMap<String, String>();

        Document doc = getXMLDocumentBuilder(result);
        Node vxml = doc.getFirstChild();
        Node answerInputNode = vxml.getChildNodes().item(0);
        Node questionIdNode = vxml.getChildNodes().item(1);
        Node sessionKeyNode = vxml.getChildNodes().item(2);
        Node form = vxml.getChildNodes().item(3);

        Node field = form.getFirstChild();

        assertNotNull(doc);
        assertEquals(doc.getChildNodes().getLength(), 1);
        assertEquals(vxml.getNodeName(), "vxml");
        assertEquals("form", form.getNodeName());
        assertEquals("answerInput", answerInputNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("questionId", questionIdNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("sessionKey", sessionKeyNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("property", field.getNodeName());

        field = form.getChildNodes().item(1);
        assertEquals("form", form.getNodeName());
        assertEquals("answerInput", answerInputNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("questionId", questionIdNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("sessionKey", sessionKeyNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("field", field.getNodeName());
        assertEquals(4, field.getChildNodes().getLength());

        if (answerInputNode.getAttributes().getNamedItem("expr") != null) {
            variablesForAnswer.put("answerInput", answerInputNode.getAttributes().getNamedItem("expr").getNodeValue()
                                            .replace("'", ""));
        }
        if (questionIdNode.getAttributes().getNamedItem("expr") != null) {
            variablesForAnswer.put("questionId", questionIdNode.getAttributes().getNamedItem("expr").getNodeValue()
                                            .replace("'", ""));
        }
        if (sessionKeyNode.getAttributes().getNamedItem("expr") != null) {
            variablesForAnswer.put("sessionKey", sessionKeyNode.getAttributes().getNamedItem("expr").getNodeValue()
                                            .replace("'", ""));
        }
        return variablesForAnswer;
    }
}
