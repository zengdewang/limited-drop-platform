package com.limiteddrop.user.seed;

import com.limiteddrop.user.entity.Customer;
import com.limiteddrop.user.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 压测种子：插入 10 万账号（cost=4 的 bcrypt，约 1-2 分钟）。
 * 运行：--spring.profiles.active=seed
 */
@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final int TARGET = 100_000;
    private static final int BATCH = 500;

    private final CustomerService customerService;
    private final BCryptPasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        long count = customerService.count();
        if (count >= TARGET) {
            log.info("已存在 {} 个用户，跳过种子", count);
            return;
        }
        long start = System.currentTimeMillis();
        List<Customer> batch = new ArrayList<>(BATCH);
        for (int i = 1; i <= TARGET; i++) {
            Customer c = new Customer();
            c.setUsername(String.format("user_%06d", i));
            c.setPasswordHash(encoder.encode("Test1234!"));
            c.setMemberLevel("NORMAL");
            batch.add(c);
            if (batch.size() == BATCH) {
                customerService.saveBatch(batch);
                batch.clear();
                if (i % 10_000 == 0) {
                    log.info("seeded {} / {} ({}ms)", i, TARGET, System.currentTimeMillis() - start);
                }
            }
        }
        if (!batch.isEmpty()) {
            customerService.saveBatch(batch);
        }
        log.info("种子完成：{} 个用户，耗时 {}ms", TARGET, System.currentTimeMillis() - start);
    }
}
