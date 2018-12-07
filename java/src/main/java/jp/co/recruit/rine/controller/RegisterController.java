package jp.co.recruit.rine.controller;

import jp.co.recruit.rine.model.Group;
import jp.co.recruit.rine.model.User;
import jp.co.recruit.rine.repository.GroupRepository;
import jp.co.recruit.rine.repository.UserRepository;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpSession;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Controller
public class RegisterController {

    @Autowired
    HttpSession session;
    @Autowired
    UserRepository userRepository;
    @Autowired
    GroupRepository groupRepository;
    @Autowired
    DataSourceTransactionManager transactionManager;
    @Autowired
    DefaultTransactionDefinition transactionDefinition;

    @RequestMapping(value = "/register", method = RequestMethod.GET)
    public String register() {
        return "register";
    }

    // TODO: 画像どうするか考える
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public String register(@RequestParam("username") String username,
                           @RequestParam("password") String password,
                           @RequestParam("lastname") String lastname,
                           @RequestParam("firstname") String firstname,
                           @RequestParam("icon") MultipartFile iconFile) throws IOException {

        validation(username, password, lastname, firstname);

        User user = new User();
        User dup = userRepository.findByUsername(username);
        if (Objects.nonNull(dup)) throw new UserDuplicationError();

        String salt = BCrypt.gensalt();
        String hash = BCrypt.hashpw(password, salt);
        user.setUsername(username);
        user.setSalt(salt);
        user.setHash(hash);
        user.setFirstname(firstname);
        user.setLastname(lastname);

        TransactionStatus status = transactionManager.getTransaction(transactionDefinition);
        try {

            if (iconFile.isEmpty()) {
                user.setIcon("default.png");
            } else {
                String ext = FilenameUtils.getExtension(iconFile.getOriginalFilename());
                if (!Objects.equals(ext, "jpeg") && !Objects.equals(ext, "jpg") &&
                        !Objects.equals(ext, "png") && !Objects.equals(ext, "gif")) {
                    user.setIcon("default.png");
                } else {
                    String icon = user.getUsername() + "-" + System.currentTimeMillis() + "."
                            + UUID.randomUUID() + "." + ext;
                    String filePath = "static/images/" + icon;
                    File uploadedIcon = new File(filePath);
                    byte[] bytes = iconFile.getBytes();
                    BufferedOutputStream uploadFileStream =
                            new BufferedOutputStream(new FileOutputStream(uploadedIcon));
                    uploadFileStream.write(bytes);
                    uploadFileStream.close();
                    user.setIcon(icon);
                }
            }

            userRepository.createUser(user);
            Group general = groupRepository.findByNameAndOwner("general", "root");
            groupRepository.addUserToGroup(general.getId(), user);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
        transactionManager.commit(status);
        session.setAttribute("user", user);
        return "redirect:/";
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(UserDuplicationError.class)
    ModelAndView userDuplicationError() {
        return new ModelAndView("register")
                .addObject("message", "既にユーザーが登録されています。");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ValidationError.class)
    ModelAndView validationError(ValidationError e) {
        return new ModelAndView("register")
                .addObject("message", e.getMessage());
    }

    private static class UserDuplicationError extends RuntimeException {
    }
    private class ValidationError extends RuntimeException {
        public ValidationError(String message) {
            super(message);
        }
    }

    private void validation(String username,
                            String password,
                            String lastname,
                            String firstname) {
        String res;
        res = validateUsername(username);
        if (!StringUtils.isEmpty(res)) throw new ValidationError(res);
        res = validatePassword(password, username);
        if (!StringUtils.isEmpty(res)) throw new ValidationError(res);
    }

    private String validateUsername(String username) {
        if (StringUtils.isEmpty(username)) {
            return "ユーザ名がありません。";
        } else if (username.length() < 5 || username.length() > 30) {
            return "ユーザ名は5文字以上30文字以下にしてください。";
        } else if (!username.matches("^[A-Za-z0-9_]+$")) {
            return "ユーザ名はアルファベットか数字にしてください。";
        }
        return null;
    }

    private String validatePassword(String password, String username) {
        if (StringUtils.isEmpty(password)) {
            return "パスワードがありません。";
        } else if (password.length() < 5 || password.length() > 30) {
            return "パスワードは5文字以上30文字以下にしてください。";
        } else if (!password.matches("^[A-Za-z0-9_]+$")) {
            return "パスワードはアルファベットか数字にしてください。";
        } else if (password.contains(username)) {
            return "パスワードにはユーザ名を含めないでください。";
        }
        return null;
    }
}
