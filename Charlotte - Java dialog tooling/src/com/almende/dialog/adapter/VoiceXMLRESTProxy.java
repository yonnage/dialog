package com.almende.dialog.adapter;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.znerd.xmlenc.XMLOutputter;

import com.almende.dialog.DDRWrapper;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.state.StringStore;
import com.almende.util.ParallelInit;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

@Path("/vxml/")
public class VoiceXMLRESTProxy {
	protected static final Logger log = Logger.getLogger(com.almende.dialog.adapter.VoiceXMLRESTProxy.class.getName()); 	
	private static final int LOOP_DETECTION=10;
	private static final String DTMFGRAMMAR="/vxml/dtmf2hash";
	
	protected String TIMEOUT_URL="/vxml/timeout";
	protected String EXCEPTION_URL="/vxml/exception";
	
	private String host = "";
	
	public static void killSession(Session session){
		
		AdapterConfig config = session.getAdapterConfig();
		if(config!=null) {
			Broadsoft bs = new Broadsoft(config);
			bs.endCall(session.getExternalSession());
		}
	}
	
	public static String dial(String address, String url, AdapterConfig config){

		address = formatNumber(address).replaceFirst("\\+31", "0")+"@outbound";
		String adapterType="broadsoft";
		String sessionKey = adapterType+"|"+config.getMyAddress()+"|"+address;
		Session session = Session.getSession(sessionKey);
		if (session == null){
			log.severe("VoiceXMLRESTProxy couldn't start new outbound Dialog, adapterConfig not found? "+sessionKey);
			return "";
		}
		session.killed=false;
		session.setStartUrl(url);
		session.setDirection("outbound");
		session.setRemoteAddress(address);
		session.setType(adapterType);
		session.setTrackingToken(UUID.randomUUID().toString());
		session.storeSession();
		
		Question question = Question.fromURL(url,address,config.getMyAddress());
		StringStore.storeString("InitialQuestion_"+sessionKey, question.toJSON());
		
		DDRWrapper.log(url,session.getTrackingToken(),session,"Dial",config);
		
		Broadsoft bs = new Broadsoft(config);
		bs.startSubscription();
		
		String extSession = bs.startCall(address);
		
		session.setExternalSession(extSession);
		session.storeSession();
		
		return sessionKey;
	}
	public static ArrayList<String> getActiveCalls(AdapterConfig config) {
		Broadsoft bs = new Broadsoft(config);
		return bs.getActiveCalls();
	}
	
	public static ArrayList<String> getActiveCallsInfo(AdapterConfig config) {
		Broadsoft bs = new Broadsoft(config);
		return bs.getActiveCallsInfo();
	}
	
	public static boolean killActiveCalls(AdapterConfig config) {
		Broadsoft bs = new Broadsoft(config);
		return bs.killActiveCalls();
	}
	
	private static String formatNumber(String phone) {
		PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
		try {
			PhoneNumber numberProto = phoneUtil.parse(phone,"NL");
			return phoneUtil.format(numberProto,PhoneNumberFormat.E164);
		} catch (NumberParseException e) {
		  log.severe("NumberParseException was thrown: " + e.toString());
		}
		return null;	
	}
	
	@Path("dtmf2hash")
	@GET
	@Produces("application/srgs+xml")
	public Response getDTMF2Hash() {
		
		String result = "<?xml version=\"1.0\"?> "+
						"<grammar mode=\"dtmf\" version=\"1.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.w3.org/2001/06/grammar http://www.w3.org/TR/speech-grammar/grammar.xsd\" xmlns=\"http://www.w3.org/2001/06/grammar\"  root=\"untilHash\" > "+
							"<rule id=\"digit\"> "+
								"<one-of> "+
									"<item> 0 </item> "+
									"<item> 1 </item> "+
									"<item> 2 </item> "+
									"<item> 3 </item> "+
									"<item> 4 </item> "+
									"<item> 5 </item> "+
									"<item> 6 </item> "+
									"<item> 7 </item> "+
									"<item> 8 </item> "+
									"<item> 9 </item> "+
									"<item> * </item> "+
								"</one-of> "+
							"</rule> "+
							"<rule id=\"untilHash\" scope=\"public\"> "+
								"<one-of> "+
									"<item repeat=\"0-\"><ruleref uri=\"#digit\"/></item> "+
									"<item> # </item> "+
								"</one-of> "+
							"</rule> "+
						"</grammar> ";
		
		return Response.ok(result).build();
	}
	
	@Path("new")
	@GET
	@Produces("application/voicexml")
	public Response getNewDialog(@QueryParam("direction") String direction,@QueryParam("remoteID") String remoteID,@QueryParam("localID") String localID, @Context UriInfo ui){
		log.warning("call started:"+direction+":"+remoteID+":"+localID);		
		this.host=ui.getBaseUri().toString().replace(":80", "");
		
		String adapterType="broadsoft";
		AdapterConfig config = AdapterConfig.findAdapterConfig(adapterType, localID);
		String sessionKey = adapterType+"|"+localID+"|"+remoteID+(direction.equals("outbound")?"@outbound":"");
		Session session = Session.getSession(sessionKey);

		String url="";
		if (direction.equals("inbound")){
			url = config.getInitialAgentURL();
			session.setStartUrl(url);
			session.setDirection("inbound");
			session.setRemoteAddress(remoteID);
			session.setType(adapterType);
			session.setPubKey(config.getPublicKey());
			session.setTrackingToken(UUID.randomUUID().toString());
		} else {
			url=session.getStartUrl();
		}
		
		String json = StringStore.getString("InitialQuestion_"+sessionKey);
		Question question = null;
		if(json!=null) {
			log.info("Getting question from cache");
			question = Question.fromJSON(json);
			StringStore.dropString("InitialQuestion_"+sessionKey);
		} else {
			question = Question.fromURL(url,remoteID,localID);
		}
		DDRWrapper.log(question,session,"Start",config);
		session.storeSession();
		
		return handleQuestion(question,remoteID,sessionKey);
	}
	
	@Path("answer")
	@GET
	@Produces("application/voicexml+xml")
	public Response answer(@QueryParam("question_id") String question_id, @QueryParam("answer_id") String answer_id, @QueryParam("answer_input") String answer_input, @QueryParam("sessionKey") String sessionKey, @Context UriInfo ui){
		this.host=ui.getBaseUri().toString().replace(":80", "");
		log.info("Received answer: "+answer_input);
		String reply="<vxml><exit/></vxml>";
		Session session = Session.getSession(sessionKey);
		if (session!=null) {
			String json = StringStore.getString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());
			if (json != null){
				Question question = Question.fromJSON(json);
				//String responder = StringStore.getString(question_id+"-remoteID");
				String responder = session.getRemoteAddress();
				if (session.killed){
					log.warning("session is killed");
					return Response.status(Response.Status.BAD_REQUEST).build();
				}
				DDRWrapper.log(question,session,"Answer");
				
				StringStore.dropString(question_id);
				StringStore.dropString(question_id+"-remoteID");
				StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());
	
				question = question.answer(responder,answer_id,answer_input);
				
				return handleQuestion(question,responder,sessionKey);
			}
		} else {
			log.warning("No session found for: "+sessionKey);
		}
		return Response.ok(reply).build();
	}
	
	@Path("timeout")
	@GET
	@Produces("application/voicexml+xml")
	public Response timeout(@QueryParam("question_id") String question_id, @QueryParam("sessionKey") String sessionKey){
		String reply="<vxml><exit/></vxml>";
		String json = StringStore.getString(question_id);
		if (json != null){
			Question question = Question.fromJSON(json);
			String responder = StringStore.getString(question_id+"-remoteID");
			Session session = Session.getSession(sessionKey);
			if (session.killed){
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			DDRWrapper.log(question,session,"Timeout");
			
			StringStore.dropString(question_id);
			StringStore.dropString(question_id+"-remoteID");
			StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());

			question = question.event("timeout", "No answer received", responder);
			
			return handleQuestion(question,responder,sessionKey);
		}
		return Response.ok(reply).build();
	}
	
	@Path("exception")
	@GET
	@Produces("application/voicexml+xml")
	public Response exception(@QueryParam("question_id") String question_id, @QueryParam("sessionKey") String sessionKey){
		String reply="<vxml><exit/></vxml>";
		String json = StringStore.getString(question_id);
		if (json != null){
			Question question = Question.fromJSON(json);
			String responder = StringStore.getString(question_id+"-remoteID");
			Session session = Session.getSession(sessionKey);
			if (session.killed){
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			DDRWrapper.log(question,session,"Timeout");
			
			StringStore.dropString(question_id);
			StringStore.dropString(question_id+"-remoteID");
			StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());

			question = question.event("exception", "Wrong answer received", responder);
			
			return handleQuestion(question,responder,sessionKey);
		}
		return Response.ok(reply).build();
	}
	
	@Path("hangup")
	@GET
	@Produces("application/voicexml+xml")
	public Response hangup(@QueryParam("direction") String direction,@QueryParam("remoteID") String remoteID,@QueryParam("localID") String localID){
		log.info("call hangup with:"+direction+":"+remoteID+":"+localID);
		
		String adapterType="broadsoft";
		
		String sessionKey = adapterType+"|"+localID+"|"+remoteID;
		Session session = Session.getSession(sessionKey);
		
		Question question = null;
		String json = StringStore.getString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());
		if(json!=null) {
			question = Question.fromJSON(json);
			String question_id = question.getQuestion_id();
			
			StringStore.dropString(question_id);
			StringStore.dropString(question_id+"-remoteID");
			StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());
		} else {
			question = Question.fromURL(session.getStartUrl(),remoteID,localID);
		}

		question.event("hangup", "Hangup", remoteID);
		DDRWrapper.log(question,session,"Hangup");
		
		handleQuestion(null,remoteID,sessionKey);
		
		return Response.ok("").build();
	}
	
	@Path("cc")
	@POST
	public Response receiveCCMessage(String xml) {
		
		log.info("Received cc: "+xml);
		
		String reply="";
		
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			
			Document dom = db.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
			Node subscriberId = dom.getElementsByTagName("subscriberId").item(0);
			
			AdapterConfig config = AdapterConfig.findAdapterConfigByUsername(subscriberId.getTextContent());
			
			Node eventData = dom.getElementsByTagName("eventData").item(0);
			// check if incall event
			if(eventData.getChildNodes().getLength()>1) {
				
				
				Node call = eventData.getChildNodes().item(1);
				
				Node personality = null;
				Node callState = null;
				Node remoteParty = null;
				@SuppressWarnings("unused")
				Node extTrackingId = null;
				
				for(int i=0;i<call.getChildNodes().getLength();i++) {
					Node node = call.getChildNodes().item(i);
					if(node.getNodeName().equals("personality")) {
						personality=node;
					} else if(node.getNodeName().equals("callState")) {
						callState=node;
					} else if(node.getNodeName().equals("remoteParty")) {
						remoteParty=node;
					} else if(node.getNodeName().equals("extTrackingId")) {
						extTrackingId=node;
					}
				}				
				
				if(callState!=null && callState.getNodeName().equals("callState")) {

					// Check if call
					if(callState.getTextContent().equals("Released")) {
						
						// Check if a sip or network call
						String type="";
						String address="";
						for(int i=0; i<remoteParty.getChildNodes().getLength();i++) {
							Node rpChild = remoteParty.getChildNodes().item(i);
							if(rpChild.getNodeName().equals("address")) {
								address=rpChild.getTextContent();
							} else if(rpChild.getNodeName().equals("callType")) {
								type=rpChild.getTextContent();
							}
						}
						
						// Check if session can be matched to call
						if(type.equals("Network") || type.equals("Group") || type.equals("Unknown")) {
							
							address = address.replace("tel:", "").replace("sip:", "");
							
							log.info("Going to format phone number: "+address);
							
							if(address.startsWith("+")) {
								address = formatNumber(address).replaceFirst("\\+31", "0");
							}
							
							String direction="inbound";
							if(personality.getTextContent().equals("Originator") && !address.contains("outbound")) {
								//address += "@outbound";
								direction="transfer";
								log.info("Transfer detected????");
							} else if(personality.getTextContent().equals("Originator")) {
								log.info("Outbound detected?????");
								direction="outbound";
							} else if(personality.getTextContent().equals("Click-to-Dial")) {
								log.info("CTD hangup detected?????");
								direction="outbound";
								
								//TODO: check if request failed
							}
							String adapterType="broadsoft";
							String sessionKey = adapterType+"|"+config.getMyAddress()+"|"+address;
							String ses = StringStore.getString(sessionKey);
							
							log.info("Session key: "+sessionKey);
							
							if(ses!=null && direction!="transfer") {
								log.info("SESSSION FOUND!! SEND HANGUP!!!");
								this.hangup(direction, address, config.getMyAddress());
							} else {
								
								if(personality.getTextContent().equals("Originator")) {
									log.info("Probably a disconnect of a redirect");
								} else if(personality.getTextContent().equals("Terminator")) {
									log.info("No session for this inbound?????");
								} else {
									log.info("What the hell was this?????");
									log.info("Session already ended?");
								}
							}
							
						} else {
							log.warning("Can't handle hangup of type: "+type+" (yet)");
						}						
					}
				}
			} else {
				Node eventName = dom.getElementsByTagName("eventName").item(0);
				if(eventName!=null && eventName.getTextContent().equals("SubscriptionTerminatedEvent")) {
					
					Broadsoft bs = new Broadsoft(config);
					bs.startSubscription();
					log.info("Start a new dialog");
				}
				
				log.info("Received a subscription update!");
			}
			
		} catch (Exception e) {
			log.severe("Something failed: "+e.getMessage());
		}
		
		
		return Response.ok(reply).build();
	}
		
	private class Return {
		ArrayList<String> prompts;
		Question question;

		public Return(ArrayList<String> prompts, Question question) {
			this.prompts = prompts;
			this.question = question;
		}
	}
	
	public Return formQuestion(Question question,String address) {
		ArrayList<String> prompts = new ArrayList<String>();
		for (int count = 0; count<=LOOP_DETECTION; count++){
			if (question == null) break;
			log.info("Going to form question of type: "+question.getType());
			String preferred_language = question.getPreferred_language();
			question.setPreferred_language(preferred_language);	
			String qText = question.getQuestion_text();
			
			if(qText!=null && !qText.equals("")) prompts.add(qText);

			if (question.getType().equalsIgnoreCase("closed")) {
				for (Answer ans : question.getAnswers()) {
					String answer = ans.getAnswer_text();
					if (answer != null && !answer.equals("")) prompts.add(answer);
				}
				break; //Jump from forloop
			} else if (question.getType().equalsIgnoreCase("comment")) {
				//question = question.answer(null, null, null);
				break;
			} else 	if (question.getType().equalsIgnoreCase("referral")) {
				if(!question.getUrl().startsWith("tel:")) {
					question = Question.fromURL(question.getUrl(),address);
					//question = question.answer(null, null, null);
//					break;
				} else {
					//break;
				}			
			} else {
				break; //Jump from forloop (open questions, etc.)
			}
		}
		return new Return(prompts, question);
	}
	
	protected String renderComment(Question question,ArrayList<String> prompts, String sessionKey){

		String handleTimeoutURL = "/vxml/timeout";
		String handleExceptionURL = "/vxml/exception";
		
		String redirectTimeoutProperty = getMediaProperty( question, MediumType.Broadsoft, MediaPropertyKey.RedirectTimeOut );
        //assign a default timeout if one is not specified
        String redirectTimeout = redirectTimeoutProperty != null ? redirectTimeoutProperty : "40s";
		
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.startTag("form");
						if (question != null && question.getType().equalsIgnoreCase("referral")){
							outputter.startTag("transfer");
								outputter.attribute("name", "thisCall");
								outputter.attribute("dest", question.getUrl());
								outputter.attribute("bridge","true");
								outputter.attribute("connecttimeout",redirectTimeout);
								
								for (String prompt : prompts){
									outputter.startTag("prompt");
										outputter.startTag("audio");
											outputter.attribute("src", prompt);
										outputter.endTag();
									outputter.endTag();
								}
								outputter.startTag("filled");
									outputter.startTag("if");
										outputter.attribute("cond", "thisCall=='noanswer'");
										outputter.startTag("goto");
											outputter.attribute("next", handleTimeoutURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
										outputter.endTag();
									outputter.startTag("elseif");
										outputter.attribute("cond", "thisCall=='busy' || thisCall=='network_busy'");
									outputter.endTag();
										outputter.startTag("goto");
											outputter.attribute("next", handleExceptionURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
										outputter.endTag();	
									outputter.startTag("else");
									outputter.endTag();
										outputter.startTag("goto");
											outputter.attribute("next", getAnswerUrl()+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
										outputter.endTag();	
									outputter.endTag();
								outputter.endTag();
							outputter.endTag();
						} else {
							outputter.startTag("block");
								for (String prompt : prompts){
									outputter.startTag("prompt");
										outputter.startTag("audio");
											outputter.attribute("src", prompt);
										outputter.endTag();
									outputter.endTag();
								}
								if(question!=null) {
									outputter.startTag("goto");
										outputter.attribute("next", getAnswerUrl()+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
									outputter.endTag();
								}
							outputter.endTag();
						}
						
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();
		} catch (Exception e) {
			log.severe("Exception in creating question XML: "+ e.toString());
		}
		return sw.toString();	
	}


	private String renderClosedQuestion(Question question,ArrayList<String> prompts,String sessionKey){
		ArrayList<Answer> answers=question.getAnswers();
		
		String handleTimeoutURL = "/vxml/timeout";

		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.startTag("menu");	
					for (String prompt : prompts){
						outputter.startTag("prompt");
							outputter.startTag("audio");
								outputter.attribute("src", prompt);
							outputter.endTag();
						outputter.endTag();
					}
					for(int cnt=0; cnt<answers.size(); cnt++){
						outputter.startTag("choice");
							outputter.attribute("dtmf", new Integer(cnt+1).toString());
							outputter.attribute("next", getAnswerUrl()+"?question_id="+question.getQuestion_id()+"&answer_id="+answers.get(cnt).getAnswer_id()+"&sessionKey="+sessionKey);
						outputter.endTag();
					}
					outputter.startTag("noinput");
						outputter.startTag("goto");
							outputter.attribute("next", handleTimeoutURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("nomatch");
						outputter.startTag("goto");
							outputter.attribute("next", getAnswerUrl()+"?question_id="+question.getQuestion_id()+"&answer_id=-1&sessionKey="+sessionKey);
						outputter.endTag();
					outputter.endTag();
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();
		} catch (Exception e) {
			log.severe("Exception in creating question XML: "+ e.toString());
		}
		return sw.toString();
	}
	private String renderOpenQuestion(Question question,ArrayList<String> prompts,String sessionKey){

		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				//outputter.attribute("xml:lang", "en-US"); //To prevent "unrecognized input" prompt
				outputter.startTag("var");
					outputter.attribute("name","answer_input");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name","question_id");
					outputter.attribute("expr", "'"+question.getQuestion_id()+"'");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name","sessionKey");
					outputter.attribute("expr", "'"+sessionKey+"'");
				outputter.endTag();
				outputter.startTag("form");
					outputter.startTag("field");
						outputter.attribute("name", "answer");
						outputter.startTag("grammar");
							outputter.attribute("mode", "dtmf");
							outputter.attribute("src", DTMFGRAMMAR);
							outputter.attribute("type", "application/srgs+xml");
						outputter.endTag();
						for (String prompt: prompts){
							outputter.startTag("prompt");
								outputter.startTag("audio");
									outputter.attribute("src", prompt);
								outputter.endTag();
							outputter.endTag();
						}
						outputter.startTag("noinput");
							outputter.startTag("reprompt");
							outputter.endTag();
						outputter.endTag();
					
						outputter.startTag("filled");
							outputter.startTag("assign");
								outputter.attribute("name", "answer_input");
								outputter.attribute("expr", "answer$.utterance.replace(' ','','g')");
							outputter.endTag();
							outputter.startTag("submit");
								outputter.attribute("next", getAnswerUrl());
								outputter.attribute("namelist","answer_input question_id sessionKey");
							outputter.endTag();
							outputter.startTag("clear");
								outputter.attribute("namelist", "answer_input answer");
							outputter.endTag();
						outputter.endTag();
					outputter.endTag();
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();	
		} catch (Exception e) {
			log.severe("Exception in creating open question XML: "+ e.toString());
		}		
		return sw.toString();
	}
	private String renderOpenQuestionAudio(Question question,ArrayList<String> prompts,String sessionKey){
		//String handleTimeoutURL = "/vxml/timeout";
		
		String storedAudiofile = this.host+"upload/"+UUID.randomUUID().toString()+".wav";
		
		Client client = ParallelInit.getClient();
		WebResource webResource = client.resource(storedAudiofile+"?url");
		String uploadURL = "";
		try {
			uploadURL = webResource.type("application/json").get(String.class);
		} catch(Exception e){
		}
		
		uploadURL = uploadURL.replace(this.host, "/");

		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.attribute("xml:lang", "nl-NL"); //To prevent "unrecognized input" prompt
				
				outputter.startTag("form");
					outputter.attribute("id", "ComposeMessage");
					outputter.startTag("record");
						outputter.attribute("name", "file");
						outputter.attribute("beep", "true");
						outputter.attribute("maxtime", "15s");
						outputter.attribute("dtmfterm", "true");
						//outputter.attribute("finalsilence", "3s");
						for (String prompt : prompts){
							outputter.startTag("prompt");
								outputter.attribute("timeout", "5s");
								outputter.startTag("audio");
									outputter.attribute("src", prompt);
								outputter.endTag();
							outputter.endTag();
						}
						outputter.startTag("noinput");
							for (String prompt : prompts){
								outputter.startTag("prompt");
									outputter.startTag("audio");
										outputter.attribute("src", prompt);
									outputter.endTag();
								outputter.endTag();
							}
							/*outputter.startTag("goto");
								outputter.attribute("next", handleTimeoutURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
							outputter.endTag();*/
						outputter.endTag();
					outputter.endTag();
					
					outputter.startTag("subdialog");
						outputter.attribute("name", "saveWav");
						outputter.attribute("src", uploadURL);
						outputter.attribute("namelist", "file");
						outputter.attribute("method", "post");
						outputter.attribute("enctype", "multipart/form-data");
						outputter.startTag("filled");
							outputter.startTag("if");
								outputter.attribute("cond", "saveWav.response='SUCCESS'");
								outputter.startTag("goto");
									outputter.attribute("next", getAnswerUrl()+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey+"&answer_input="+URLEncoder.encode(storedAudiofile, "UTF-8"));
								outputter.endTag();
							outputter.startTag("else");
							outputter.endTag();
								for (String prompt : prompts){
									outputter.startTag("prompt");
										outputter.startTag("audio");
											outputter.attribute("src", prompt);
										outputter.endTag();
									outputter.endTag();
								}
							outputter.endTag();
						outputter.endTag();
					outputter.endTag();
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();	
		} catch (Exception e) {
			log.severe("Exception in creating open question XML: "+ e.toString());
		}		
		return sw.toString();
	}
	
	private Response handleQuestion(Question question,String remoteID,String sessionKey){
		String result="<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form><block><exit/></block></form></vxml>";
		Return res = formQuestion(question,remoteID);
		if(question !=null && !question.getType().equalsIgnoreCase("comment"))
			question = res.question;
		
		log.info( "question formed at handleQuestion is: "+ question );
		log.info( "prompts formed at handleQuestion is: "+ res.prompts );
		
                if ( question != null )
                {
                    question.generateIds();
                    StringStore.storeString( question.getQuestion_id(), question.toJSON() );
                    StringStore.storeString( question.getQuestion_id() + "-remoteID", remoteID );
        
                    Session session = Session.getSession( sessionKey );
                    StringStore.storeString( "question_" + session.getRemoteAddress() + "_"
                                                 + session.getLocalAddress(), question.toJSON() );
        
                    if ( question.getType().equalsIgnoreCase( "closed" ) )
                    {
                        result = renderClosedQuestion( question, res.prompts, sessionKey );
                    }
                    else if ( question.getType().equalsIgnoreCase( "open" ) )
                    {
                        result = renderOpenQuestion( question, res.prompts, sessionKey );
                    }
                    else if ( question.getType().equalsIgnoreCase( "openaudio" ) )
                    {
                        result = renderOpenQuestionAudio( question, res.prompts, sessionKey );
                    }
                    else if ( question.getType().equalsIgnoreCase( "referral" ) )
                    {
                        if ( question.getUrl().startsWith( "tel:" ) )
                        {
                            result = renderComment( question, res.prompts, sessionKey );
                        }
                    }
                    else if ( res.prompts.size() > 0 )
                    {
                        result = renderComment( question, res.prompts, sessionKey );
                    }
                }
                else if ( res.prompts.size() > 0 )
                {
                    result = renderComment( null, res.prompts, sessionKey );
                }
                else
                {
                    log.info( "Going to hangup? So clear Session?" );
                }
		log.info("Sending xml: "+result);
		return Response.ok(result).build();
	}

    private String getMediaProperty( Question question, MediumType mediumType, MediaPropertyKey key )
    {
        Collection<MediaProperty> media_Properties = question.getMedia_Properties();
        for ( MediaProperty mediaProperties : media_Properties )
        {
            if ( mediaProperties.getMedium() == mediumType )
            {
                return mediaProperties.getProperties().get( key );
            }
        }
        return null;
    }
	protected String getAnswerUrl() {
		return "/vxml/answer";
	}
}
