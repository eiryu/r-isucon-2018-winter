package jp.co.recruit.rine.controller;

import jp.co.recruit.rine.model.User;
import jp.co.recruit.rine.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpSession;
import java.util.Objects;

@Controller
public class LoginController {

    @Autowired
    HttpSession session;
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    UserRepository userRepository;

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login() {
        return "login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String login(@RequestParam("username") String username,
                        @RequestParam("password") String password) {
        User user = userRepository.findByUsername(username);
        if (Objects.isNull(user)) throw new AuthenticationError();
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(password, user.getHash())) throw new AuthenticationError();
        session.setAttribute("user", user);
        return "redirect:/";
    }

    @RequestMapping(value ="/logout", method = RequestMethod.POST)
    public String logout() {
        session.invalidate();
        return "redirect:/";
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(AuthenticationError.class)
    ModelAndView authenticationError() {
        return new ModelAndView("login")
                .addObject("message", "ユーザ名もしくはパスワードが間違っています。");
    }

    public static class AuthenticationError extends RuntimeException {
    }

}