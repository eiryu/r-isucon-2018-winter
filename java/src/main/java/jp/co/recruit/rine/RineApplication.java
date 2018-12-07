package jp.co.recruit.rine;

import jp.co.recruit.rine.controller.LoginController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.SessionTrackingMode;
import java.util.Collections;

@SpringBootApplication
public class RineApplication {
    @Bean
    ServletContextInitializer servletContextInitializer() {
        // Sessionの維持をCookieのみにして、URLにjsessionidが付かないようにする
        return servletContext -> servletContext.setSessionTrackingModes(
                Collections.singleton(SessionTrackingMode.COOKIE));
    }

    public static void main(String[] args) {
        SpringApplication.run(RineApplication.class, args);
    }
}
