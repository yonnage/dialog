package com.almende.dialog.adapter;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.state.StringStore;
import com.almende.dialog.util.KeyServerLib;
import com.almende.eve.agent.Agent;
import com.almende.eve.agent.annotation.Access;
import com.almende.eve.agent.annotation.AccessType;
import com.almende.eve.json.annotation.Name;
import com.almende.eve.json.annotation.Required;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class DialogAgent extends Agent {
	
	public DialogAgent(){
		super();
		ParallelInit.startThreads();
	}
	
	@Access(AccessType.UNAVAILABLE)
	public Question getQuestion(String url, String id, String json){
		Question question=null;
		if (id != null) question = Question.fromJSON(StringStore.getString(id));
		if (json != null) question = Question.fromJSON(json);
		if (question == null && url != null) question = Question.fromURL(url);
		question.generateIds();
		return question;
	}
	
	public String killCall(String sessionKey){
		Session session = Session.getSession(sessionKey);
		if (session == null) return "unknown";
		session.kill();
		return "ok";
	}
	public String outboundCall(@Name("address") String address, 
							   @Name("url") String url, 
							   @Name("adapterType") @Required(false) String adapterType, 
							   @Name("adapterID") @Required(false) String adapterID, 
							   @Name("publicKey") String pubKey,
							   @Name("privateKey") String privKey){
		
		// Authenticate keys with key-server
		ArrayNode adapterList = KeyServerLib.getAllowedAdapterList(pubKey, privKey, adapterType);
		
		if(adapterList==null)
			return "Invalid key provided";
		try {
			AdapterConfig config = AdapterConfig.findAdapterConfig(adapterID, adapterType,adapterList);
		
			if(config!=null) {
				if (adapterType.toUpperCase().equals("XMPP")){
					return "{'sessionKey':'"+new XMPPServlet().startDialog(address,url,config)+"'}";
				} else if (adapterType.toUpperCase().equals("BROADSOFT")){
					return "{'sessionKey':'"+VoiceXMLRESTProxy.dial(address,url,config)+"'}";
				} else if (adapterType.toUpperCase().equals("MAIL")){
					return "{'sessionKey':'"+new MailServlet().startDialog(address,url,config)+"'}";
				} else if (adapterType.toUpperCase().equals("SMS")){
					return "{'sessionKey':'"+new AskSmsServlet().startDialog(address,url,config)+"'}";
				} else {
					return "Unknown type given: either broadsoft or xmpp or phone or mail";
				}
			} else {
				return "Invalid adapter found";
			}
		} catch(Exception ex) {
			return "Error in finding adapter";
		}
	}
	
	public String startDialog(@Name("question_url") @Required(false) String url,
							  @Name("myid") @Required(false) String id, 
							  @Name("question_json") @Required(false) String json,
							  @Name("expanded_texts") @Required(false) String expanded_texts){
		String reply = "";
		Question question = getQuestion(url,id,json);
		if (expanded_texts == null) expanded_texts = "false";
		if (question != null) reply = question.toJSON(new Boolean(expanded_texts));
		if (id != null){
			if (question == null){
        		StringStore.dropString(id);
        	} else {
        		StringStore.storeString(id, reply);
        	}
		}
		return reply;
	}
	public String answer(@Name("question_url") @Required(false) String url,
						 @Name("myid") @Required(false) String id,
						 @Name("question_json") @Required(false) String json,
						 @Name("answer_input") @Required(false) String answer_input,
						 @Name("answer_id") @Required(false) String answer_id,
						 @Name("expanded_texts") @Required(false) String expanded_texts){
		String reply = "";
		Question question = getQuestion(url,id,json);
		if (question != null) question = question.answer("",answer_id, answer_input);
		if (expanded_texts == null) expanded_texts = "false";
		if (question != null) reply = question.toJSON(new Boolean(expanded_texts));
		if (id != null){
			if (question == null){
        		StringStore.dropString(id);
        	} else {
        		StringStore.storeString(id, reply);
        	}
		}
		return reply;
	}
	
	@Override
	public String getDescription() {
		return "Dialog handling agent";
	}

	@Override
	public String getVersion() {
		return "0.1";
	}

}

