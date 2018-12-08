package jp.co.recruit.rine.controller;

import jp.co.recruit.rine.component.ChatHandler;
import jp.co.recruit.rine.model.Chat;
import jp.co.recruit.rine.model.Group;
import jp.co.recruit.rine.model.User;
import jp.co.recruit.rine.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpSession;
import java.util.*;

@Controller
public class ChatController {

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

    @RequestMapping(value = "/groups/{owner}/{name}", method = RequestMethod.GET)
    public ModelAndView show(@PathVariable("owner") String owner,
                             @PathVariable("name") String name) {
        User userFromSession = (User) session.getAttribute("user");
        if (Objects.isNull(userFromSession)) {
            return new ModelAndView("redirect:/login");
        }

        Group group = groupRepository.findByNameAndOwner(name, owner);
        if (Objects.isNull(group)) {
            throw new GroupNotFoundError();
        }
        List<Integer> chatIds = belongsChatGroupRepository.getChatIdList(group);
        if (!belongsUserGroupRepository.isBelonging(group, userFromSession)) {
            throw new GroupNotFoundError();
        }

        if (chatIds.isEmpty()) {
            return new ModelAndView("chat")
                    .addObject("group", group)
                    .addObject("chats", new ArrayList<>());
        }

        List<Chat> chats = new ArrayList<>();

        TransactionStatus status = transactionManager.getTransaction(transactionDefinition);
        try {
            List<Chat> chatList = chatRepository.findChats(chatIds);

            for(Chat chat: chatList) {
                User user = userRepository.findByUsername(chat.getCommentBy());
                readChatRepository.markRead(chat.getId(), userFromSession);
                try {
                    Integer cnt = readChatRepository.getReadCount(chat.getId());
                    Map<String, Object> hash = new HashMap<>();
                    hash.put("eventName", "read");
                    hash.put("id", chat.getId());
                    hash.put("username", userFromSession.getUsername());
                    hash.put("count", cnt);
                    chatHandler.broadcast(hash);
                } catch (Exception e) {
                    // ignore
                }
                Integer cnt = readChatRepository.getReadCount(chat.getId());
                chat.setCommentUser(user);
                chat.setCount(cnt);
                chats.add(chat);
            }
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
        transactionManager.commit(status);
        return new ModelAndView("chat")
                .addObject("group", group)
                .addObject("chats", chats);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(GroupNotFoundError.class)
    ModelAndView groupNotFound() {
        return new ModelAndView("chat")
                .addObject("message", "not found group");
    }

    public static class GroupNotFoundError extends RuntimeException {
    }
}
