package com.limiteddrop.flashsale.config;

import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.common.api.Result;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class FlashsaleConfig {

    @Bean
    public DefaultRedisScript<Long> buyScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/buy.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> releaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/release.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /** 异步写审计日志，绝不能阻塞抢购响应路径 */
    @Bean
    public Executor asyncLogExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(2000);
        ex.setThreadNamePrefix("flash-log-");
        ex.initialize();
        return ex;
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
