package com.limiteddrop.user.config;

import com.limiteddrop.common.auth.JwtUtil;
import com.limiteddrop.common.api.Result;
import com.limiteddrop.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class UserConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${app.jwt.secret}") String secret,
                           @Value("${app.jwt.ttl-millis}") long ttlMillis) {
        return new JwtUtil(secret, ttlMillis);
    }

    /** cost=4：压测需要 10 万账号，cost 10 会慢到不可接受 */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(4);
    }

    @RestControllerAdvice
    public static class GlobalExceptionHandler {

        @ExceptionHandler(ApiException.class)
        public ResponseEntity<Result<Void>> handleApi(ApiException e) {
            int status = (e.getCode() >= 400 && e.getCode() < 600) ? e.getCode() : 200;
            return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMessage()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
            String msg = e.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + ": " + f.getDefaultMessage())
                    .findFirst().orElse("参数错误");
            return ResponseEntity.badRequest().body(Result.error(400, msg));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Result<Void>> handleOther(Exception e) {
            return ResponseEntity.internalServerError().body(Result.error(500, e.getMessage()));
        }
    }
}
