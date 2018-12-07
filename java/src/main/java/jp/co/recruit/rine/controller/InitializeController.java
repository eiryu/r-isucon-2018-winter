package jp.co.recruit.rine.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@RestController
public class InitializeController {

    @RequestMapping(value = "/initialize", method = RequestMethod.GET)
    public HashMap<String, String> initialize() {
        try {
            String[] cmd = {
                "bash",
                "../sql/db_setup.sh"
            };
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            return new HashMap<String, String>() {{
                put("message", "initialized");
            }};
        } catch (Exception e) {
            throw new InitializeError(e.getMessage());
        }
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(InitializeError.class)
    public HashMap<String, String> initializeError(InitializeError e) {
        return new HashMap<String, String>() {{
            put("message", e.getMessage());
        }};
    }

    private class InitializeError extends RuntimeException {
        public InitializeError(String message) { super(message); }
    }
}
