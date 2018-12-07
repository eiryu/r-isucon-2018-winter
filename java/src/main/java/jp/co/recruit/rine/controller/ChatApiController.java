package jp.co.recruit.rine.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jp.co.recruit.rine.component.ChatHandler;
import jp.co.recruit.rine.model.Group;
import jp.co.recruit.rine.model.User;
import jp.co.recruit.rine.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
public class ChatApiController {

    @Autowired
    Gson gson;
    @Autowired
    HttpSession session;
    @Autowired
    DataSourceTransactionManager transactionManager;
    @Autowired
    DefaultTransactionDefinition transactionDefinition;
    @Autowired
    ChatHandler chatHandler;
    @Autowired
    UserRepository userRepository;
    @Autowired
    GroupRepository groupRepository;
    @Autowired
    ChatRepository chatRepository;
    @Autowired
    ReadChatRepository readChatRepository;
    @Autowired
    BelongsChatGroupRepository belongsChatGroupRepository;
    @Autowired
    BelongsUserGroupRepository belongsUserGroupRepository;

    @RequestMapping(value = "/groups/{owner}/{name}", method = RequestMethod.POST)
    public HashMap<String, Object> post(@PathVariable("owner") String owner,
                                        @PathVariable("name") String name,
                                        @RequestBody String body) throws IOException {

        User user = (User) session.getAttribute("user");
        if (Objects.isNull(user)) throw new UnauthorizedError();

        Map<String, String> parsed = gson.fromJson(body, new TypeToken<Map<String, String>>(){}.getType());
        String comment = parsed.getOrDefault("value", "");

        Group group = groupRepository.findByNameAndOwner(name, owner);
        if (Objects.isNull(group)) throw new GroupNotFoundError();
        if (!belongsUserGroupRepository.isBelonging(group, user)) throw new GroupNotFoundError();

        Integer chatId;

        TransactionStatus status = transactionManager.getTransaction(transactionDefinition);
        try {
            chatId = chatRepository.insertChat(comment, user);
            belongsChatGroupRepository.insertBelongsChatGroup(chatId, group);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
        transactionManager.commit(status);

        Map<String, Object> hash = new HashMap<>();
        hash.put("eventName", "message");
        hash.put("comment", comment);
        hash.put("commentBy", user.getFiltered());
        hash.put("groupname", group.getName());
        hash.put("chatId", chatId);
        hash.put("count", 0);
        try {
            chatHandler.broadcast(hash);
        } catch (Exception e) {
            // ignore
        }

        return new HashMap<String, Object>() {{
            put("message", "ok");
            put("chatId", chatId);
        }};

    }

    @RequestMapping(value = "/groups/{owner}/{name}/{chatId}", method = RequestMethod.POST)
    public HashMap<String, Object> read(@PathVariable("owner") String owner,
                                        @PathVariable("name") String name,
                                        @PathVariable("chatId") Integer chatId) throws IOException {
        User user = (User) session.getAttribute("user");
        if (Objects.isNull(user)) throw new UnauthorizedError();
        Group group = groupRepository.findByNameAndOwner(name, owner);
        if (Objects.isNull(group)) throw new GroupNotFoundError();
        if (!belongsUserGroupRepository.isBelonging(group, user)) throw new GroupNotFoundError();
        if (Objects.isNull(belongsChatGroupRepository.findById(group, chatId))) throw new ChatNotFoundError();

        TransactionStatus status = transactionManager.getTransaction(transactionDefinition);
        try {
            readChatRepository.markRead(chatId, user);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
        transactionManager.commit(status);

        Integer cnt = readChatRepository.getReadCount(chatId);
        Map<String, Object> hash = new HashMap<>();
        hash.put("eventName", "read");
        hash.put("id", chatId);
        hash.put("username", user.getUsername());
        hash.put("count", cnt);

        try {
            chatHandler.broadcast(hash);
        } catch (Exception e) {
            // ingore
        }

        return new HashMap<String, Object>() {{
            put("message", "ok");
            put("chatId", chatId);
        }};
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(UnauthorizedError.class)
    public HashMap<String, String> unauthorized() {
        return new HashMap<String, String>() {{
            put("message", "unauthorized");
        }};
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(GroupNotFoundError.class)
    public HashMap<String, String> groupNotFound() {
        return new HashMap<String, String>() {{
            put("message", "not found group");
        }};
    }

    public static class UnauthorizedError extends RuntimeException {
    }
    public static class GroupNotFoundError extends RuntimeException {
    }
    public static class ChatNotFoundError extends RuntimeException {
    }


}
