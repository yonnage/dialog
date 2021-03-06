package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.DateTime;
import org.scribe.model.Token;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Facebook;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class PrivateFacebookServlet extends TextServlet {

    private static final long serialVersionUID = -4535701650882780533L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        ObjectMapper om = ParallelInit.getObjectMapper();
        Facebook fb = null;
        PrintWriter out = resp.getWriter();
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters(getAdapterType(), null, null);
        for(AdapterConfig config : adapters) {

            ArrayNode allMessages = om.createArrayNode();
            fb = new Facebook(new Token(config.getAccessToken(), config.getAccessTokenSecret()));
            // Get last processed message date

            // Private messages
            // TODO: processMessage doesn't work
            ArrayList<String> threads = fb.getThreads();
            for(String threadId : threads) {
                boolean process=true;
                String since = Session.getString(getAdapterType()+"_DM_"+threadId);
                if(since==null) {
                    since="0";
                    process=false;
                }

                DateTime last = new DateTime(since);
                ArrayNode messages = fb.getMessages(threadId, (last.toDate().getTime()/1000)+"");
                for(JsonNode message : messages) {

                    //proccess message
                    if(process) {

                        TextMessage msg = new TextMessage();
                        msg.setAddress(message.get("from").get("id").asText());
                        msg.setRecipientName(message.get("from").get("name").asText());
                        msg.setBody(message.get("message").asText());
                        msg.setLocalAddress(config.getMyAddress());

                        try
                        {
                            processMessage(msg);
                        } catch (Exception e)
                        {
                            log.severe(e.getLocalizedMessage());
                            e.printStackTrace();
                        }
                    }

                    DateTime date = new DateTime(message.get("created_time").asText());
                    if(last.isBefore(date))
                        last = date;

                    //allMessages.add(message);
                }

                Session.storeString(getAdapterType()+"_DM_"+threadId, "0");
            }

            out.println(allMessages.toString());
        }
    }

    @Override
    protected int sendMessage(String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config, String accountId, DDRRecord ddrRecord) {

        Facebook fb = new Facebook(new Token(config.getAccessToken(), config.getAccessTokenSecret()));
        fb.sendMessage(message, to, toName);
        return 1;
    }

    @Override
    protected int broadcastMessage(String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId,
        DDRRecord ddrRecord) throws Exception {

        return 1;
    }

    @Override
    protected TextMessage receiveMessage(HttpServletRequest req,
                                         HttpServletResponse resp) throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected String getServletPath() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected String getAdapterType() {
        return AdapterAgent.ADAPTER_TYPE_FACEBOOK;
    }

    @Override
    protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        // TODO Auto-generated method stub

    }
    
    @Override
    protected DDRRecord createDDRForIncoming(AdapterConfig adapterConfig, String accountId, String fromAddress,
        String message, Session session) throws Exception {

        return DDRUtils.createDDRRecordOnIncomingCommunication(adapterConfig, accountId, fromAddress, message,
                                                               session);
    }

    @Override
    protected DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig, String accountId, String senderName,
        Map<String, String> toAddress, String message, Map<String, Session> sessionKeyMap) throws Exception {

        //add costs with no.of messages * recipients
        return DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, accountId, toAddress, message,
                                                               sessionKeyMap);
    }

    @Override
    protected String getProviderType() {

        return AdapterAgent.ADAPTER_TYPE_FACEBOOK;
    }
}
