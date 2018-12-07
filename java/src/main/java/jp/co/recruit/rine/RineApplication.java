package jp.co.recruit.rine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import javax.servlet.SessionTrackingMode;
import java.util.Collections;

@SpringBootApplication
@EnableRedisHttpSession
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
