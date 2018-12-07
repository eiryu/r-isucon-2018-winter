package jp.co.recruit.rine.component;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatHandler extends TextWebSocketHandler {
    private Map<String, WebSocketSession> sessionMap_ = new ConcurrentHashMap<>();

    @Autowired
    Gson gson;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionMap_.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionMap_.remove(session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        for (Map.Entry<String, WebSocketSession> entry : sessionMap_.entrySet()) {
            entry.getValue().sendMessage(message);
        }
    }

    public void broadcast(Map<String, Object> message) throws IOException {
        for (Map.Entry<String, WebSocketSession> entry : sessionMap_.entrySet()) {
            entry.getValue().sendMessage(new TextMessage(gson.toJson(message)));
        }
    }
}
