package jp.co.recruit.rine;

import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Map;

public class StandardWebSocketSessionCustom extends StandardWebSocketSession implements Serializable {

    public StandardWebSocketSessionCustom(HttpHeaders headers, Map<String, Object> attributes, InetSocketAddress localAddress, InetSocketAddress remoteAddress) {
        super(headers, attributes, localAddress, remoteAddress);
    }

    public StandardWebSocketSessionCustom(HttpHeaders headers, Map<String, Object> attributes, InetSocketAddress localAddress, InetSocketAddress remoteAddress, Principal user) {
        super(headers, attributes, localAddress, remoteAddress, user);
    }
}
