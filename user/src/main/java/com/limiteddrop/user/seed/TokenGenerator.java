package com.limiteddrop.user.seed;

import com.limiteddrop.common.auth.JwtUtil;
import com.limiteddrop.user.entity.Customer;
import com.limiteddrop.user.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 离线为 JMeter 生成 tokens.csv（userId,token），不走登录接口，避免 10 万次登录压垮压测前置。
 * 运行：--spring.profiles.active=token
 */
@Slf4j
@Component
@Profile("token")
@RequiredArgsConstructor
public class TokenGenerator implements ApplicationRunner {

    private final CustomerMapper customerMapper;
    private final JwtUtil jwtUtil;

    @Value("${app.token-output:../jmeter/tokens.csv}")
    private String output;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        long start = System.currentTimeMillis();
        try (PrintWriter w = new PrintWriter(output, StandardCharsets.UTF_8)) {
            w.println("userId,token");
            for (Customer c : customerMapper.selectList(null)) {
                w.println(c.getId() + "," + jwtUtil.createToken(c.getId(), c.getUsername()));
            }
        }
        log.info("tokens.csv 已生成：{}，耗时 {}ms", output, System.currentTimeMillis() - start);
    }
}
