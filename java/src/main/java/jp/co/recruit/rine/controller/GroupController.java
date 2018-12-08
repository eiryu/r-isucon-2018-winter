package jp.co.recruit.rine.controller;

import jp.co.recruit.rine.model.Group;
import jp.co.recruit.rine.model.User;
import jp.co.recruit.rine.repository.BelongsChatGroupRepository;
import jp.co.recruit.rine.repository.BelongsUserGroupRepository;
import jp.co.recruit.rine.repository.GroupRepository;
import jp.co.recruit.rine.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Controller
public class GroupController {
    @Autowired
    HttpSession session;
    @Autowired
    DataSourceTransactionManager transactionManager;
    @Autowired
    DefaultTransactionDefinition transactionDefinition;
    @Autowired
    UserRepository userRepository;
    @Autowired
    GroupRepository groupRepository;
    @Autowired
    BelongsUserGroupRepository belongsUserGroupRepository;
    @Autowired
    BelongsChatGroupRepository belongsChatGroupRepository;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String index() {
        return "redirect:/groups";
    }

    @RequestMapping(value = "/groups", method = RequestMethod.GET)
    public ModelAndView groups() {

        User user = (User) session.getAttribute("user");
        if (Objects.isNull(user)) return new ModelAndView("redirect:/login");

        List<Group> groups = groupRepository.getGroupsByUser(user);
        return new ModelAndView("groups").addObject("groups", groups);
    }

    @RequestMapping(value = "/groups", method = RequestMethod.POST)
    public String createGroup(@RequestParam("groupname") String groupName,
                              @RequestParam("usernames") String usernamesParam) {

        User user = (User) session.getAttribute("user");
        if (Objects.isNull(user)) return "redirect:/login";
        List<Group> groups = groupRepository.getGroupsByUser(user);
        String res = validateGroupName(groupName);
        if (!StringUtils.isEmpty(res)) throw new ValidationError(res, groups);

        TransactionStatus status = transactionManager.getTransaction(transactionDefinition);
        try {
            Integer groupId = groupRepository.createOrUpdateGroup(user, groupName);

            List<String> usernames = new ArrayList<>();
            usernames.add(user.getUsername());
            usernames.addAll(Arrays.asList(usernamesParam.split(",")));
            for (String username: usernames) {
                String un = username.trim();
                if (StringUtils.isEmpty(un)) {
                    continue;
                }
                User u = userRepository.findByUsername(un);
                if (Objects.isNull(u)) {
                    throw new UserNotExistError();
                }
                groupRepository.addUserToGroup(groupId, u);
            }
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
        transactionManager.commit(status);
        return "redirect:/groups";
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(UserNotExistError.class)
    ModelAndView userNotExistError() {
        return new ModelAndView("groups")
                .addObject("message", "存在しないユーザが指定されています");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ValidationError.class)
    ModelAndView validationError(ValidationError e) {
        return new ModelAndView("groups")
                .addObject("groups", e.groups)
                .addObject("message", e.getMessage());
    }

    private String validateGroupName(String groupname) {
        if (StringUtils.isEmpty(groupname)) {
            return "グループ名がありません。";
        } else if (groupname.length() < 2 || groupname.length() > 20) {
            return "グループ名は2文字以上20文字以下にしてください。";
        }
        return null;
    }

    private static class UserNotExistError extends RuntimeException {
    }
    private class ValidationError extends RuntimeException {
        private List<Group> groups;

        public ValidationError(String message, List<Group> groups) {
            super(message);
            this.groups = groups;
        }
    }
}
