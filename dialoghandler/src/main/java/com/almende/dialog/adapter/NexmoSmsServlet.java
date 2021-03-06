package com.almende.dialog.adapter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CM;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.utils.PhoneNumberUtils;

public class NexmoSmsServlet extends TextServlet {

	protected static final Logger log = Logger
			.getLogger("DialogHandler");
	private static final long serialVersionUID = 2762467148217411999L;
	
	// Info of MessageBird
	private static final String servletPath = "/sms/nm/";
	private static final boolean USE_KEYWORDS = true;
	
    @Override
    protected int sendMessage(String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config, String accountId, DDRRecord ddrRecord) throws Exception {

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(to, toName);
        return broadcastMessage(message, subject, from, fromName, addressNameMap, extras, config, accountId, ddrRecord);
    }

    @Override
    protected int broadcastMessage(String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId,
        DDRRecord ddrRecord) throws Exception {

        String[] tokens = config.getAccessToken().split("\\|");
        CM cm = new CM(tokens[0], tokens[1], config.getAccessTokenSecret());
        return cm.broadcastMessage(message, subject, from, senderName, addressNameMap, extras, config, accountId,
                                   ddrRecord);
    }

    @Override
    protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        TextMessage msg = null;

        String localAddress = req.getParameter("to").replaceFirst("31", "0");
        String address = PhoneNumberUtils.formatNumber(req.getParameter("msisdn").replaceFirst("31", "0"), null);
        if (address != null) {
            msg = new TextMessage(USE_KEYWORDS);
            msg.setLocalAddress(localAddress);
            msg.setAddress(address);
            msg.setBody(req.getParameter("text"));
        }
        else {
            log.severe("Sender address is invalid: " + req.getParameter("msisdn"));
        }
        return msg;
    }

	@Override
	protected String getServletPath() {
		return servletPath;
	}

	@Override
	protected String getAdapterType() {
		return AdapterAgent.ADAPTER_TYPE_SMS;
	}

	@Override
	protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {}
	
	@Override
	protected String getNoConfigMessage() {
		return "U dient het juiste keyword mee te geven.";
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
        return DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, accountId, senderName, toAddress,
            CM.countMessageParts(message, toAddress.size()), message, sessionKeyMap);
    }

    @Override
    protected String getProviderType() {

        return AdapterProviders.NEXMO.toString();
    }
}
