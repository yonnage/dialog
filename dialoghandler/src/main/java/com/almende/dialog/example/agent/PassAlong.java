package com.almende.dialog.example.agent;

import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.Settings;
import com.almende.dialog.model.AnswerPost;
import com.almende.dialog.model.Session;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;


@Path("/passAlong/")
public class PassAlong {

	private static final String URL="http://"+Settings.HOST+"/passAlong/";
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	static final ObjectMapper om =ParallelInit.getObjectMapper();
	
	private String getQuestion(String question_no,String responder){
		String result = null;
		Integer questionNo = Integer.parseInt(question_no);
		switch (questionNo){
			case 2:
			case 4:
				result= "{requester:\""+URL+"id\",question_text:\""+URL+"questions/"+questionNo+"\",type:\"comment\",answers:[]}";
				break;
			case 3:
				result= "{requester:\""+URL+"id\",question_text:\""+URL+"questions/"+questionNo+"/"+responder+"\",type:\"closed\",answers:["+
						"{answer_text:\""+URL+"answers/31\",callback:\""+URL+"questions/31\"},"+
						"{answer_text:\""+URL+"answers/32\",callback:\""+URL+"questions/4\"}"+
						"]}";
				break;
			default:
				result= "{requester:\""+URL+"id\",question_text:\""+URL+"questions/"+questionNo+"\",type:\"open\",answers:["+
						"{answer_text:\"\",callback:\""+URL+"questions/"+(questionNo+1)+"\"}"+
						"]}";
				break;
		}
		return result;
	}

	@GET
	@Path("/id/")
	public Response getId(){
		return Response.ok("{ url:\""+URL+"\",nickname:\"PassAlong\"}").build();
	}

	@GET
	@Produces("application/json")
	public Response firstQuestion(@QueryParam("preferred_medium") String preferred_medium,@QueryParam("question_no") String question_no,@QueryParam("responder") String responder){
		if (question_no == null || question_no.equals("")){
			question_no = "0";
		}
		if (responder == null){
			responder = "";
		}
		return Response.ok(getQuestion(question_no,responder)).build();
	}
	
	@Path("/questions/{question_no}")
	@POST
	@Produces("application/json")
	public Response answerQuestion(String answer_json, @PathParam("question_no") String question_no){
		Integer questionNo = Integer.parseInt(question_no);
		String answer_input="";
		String responder="";
		try {
			AnswerPost answer = om.readValue(answer_json,AnswerPost.class); 
			answer_input=answer.getAnswer_text();
			responder = answer.getResponder();
			
		} catch (Exception e){
			log.severe(e.toString());
		}
		if (!responder.equals("")){
			switch (questionNo){
				case 1: //Get address, store somewhere
					Session.storeString(responder+"_passAlong_address", answer_input);
					Session.storeString(answer_input+"_passAlong_address", responder); //For the return path!:)
					break;
				case 31:
					question_no="1";
					break;
				case 2: //Get message, schedule outbound call
					Session.storeString(responder+"_passAlong_message", answer_input);
					Client client = ParallelInit.getClient();
					WebResource wr = client.resource("http://"+Settings.HOST+"/rpc");
					//TODO: make this somewhat configurable
					String account = "440cb920-dbdf-11e1-b243-00007f000001";
					String token = "440ce030-dbdf-11e1-b243-00007f000001";
					String request = "{\"id\":1, \"method\":\"outboundCall\", \"params\":{"
							+"\"address\":\""+Session.getString( responder+"_passAlong_address") +"\","
							+"\"url\":\""+URL+"?question_no=3&responder="+responder+"\","
							+"\"type\":\"gtalk\",\"account\":\""+account+"\",\"token\":\""+token+"\""
							+"}}";
					wr.type("application/json").post(String.class,request);
					break;
			}
		}
		return Response.ok(getQuestion(question_no,responder)).build();
	}
	
	@Path("/questions/{question_no}/{responder}")
	@GET
	@Produces("text/plain")
	public  Response getQuestionText(@PathParam("question_no") String question_no,
									@QueryParam("preferred_language") String preferred_language, 
									@PathParam("responder") String responder ){
		Integer questionNo = Integer.parseInt(question_no);
		String result = "";
		String message = "";
		if (responder != null && !responder.equals("")){
			message = Session.getString(responder+"_passAlong_message");
		}
		if (preferred_language != null && preferred_language.startsWith("en")){
			switch (questionNo){
				case 0: result="Hi, to whom should I pass a message?"; break;
				case 1: result="What message should I deliver?"; break;
				case 2: result="Right, I'm on it!"; break;
				case 3: result="I have a message from '"+responder+"' for you:\n\""+
							   message+"\"\n"+
							   "Do you want to send say something back?"; break;
				case 4: result="Okay, goodbye!"; break;
				default: result="Eehhh??!?";
			}
		} else{
			switch (questionNo){
			case 0: result="Hoi, aan wie kan ik iets doorgeven?"; break;
			case 1: result="Wat moet ik doorgeven?"; break;
			case 2: result="Prima, ik regel het!"; break;
			case 3: result="Ik heb een bericht van '"+responder+"' voor je:\n\""+
							   message+"\"\n"+
						   "Wil je nog iets terugzeggen?"; break;
			case 4: result="Prima, tot horens!"; break;
			default: result="Eehhh??!?";
			}
		}
		return Response.ok(result).build();
	}
	@Path("/questions/{question_no}")
	@GET
	@Produces("text/plain")
	public  Response getQuestionText(@PathParam("question_no") String question_no, @QueryParam("preferred_language") String preferred_language ){
		return getQuestionText(question_no,preferred_language,"");
	}
	
	@Path("/answers/{answer_no}")
	@GET
	@Produces("text/plain")
	public Response getAnswerText(@PathParam("answer_no") String answer_no, @QueryParam("preferred_language") String preferred_language){
		Integer answerNo = Integer.parseInt(answer_no);
		String result = "";
		if (preferred_language != null && preferred_language.startsWith("en")){
			switch (answerNo){
				case 31: result="Yes"; break;
				case 32: result="No"; break;
				default: result="Eehhh??!?";
			}
		} else{
			switch (answerNo){
				case 31: result="Ja"; break;
				case 32: result="Nee"; break;
				default: result="Eehhh??!?";
			}
		}
		return Response.ok(result).build();
	}
}

