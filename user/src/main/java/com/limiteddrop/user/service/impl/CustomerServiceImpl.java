package com.limiteddrop.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.limiteddrop.user.entity.Customer;
import com.limiteddrop.user.mapper.CustomerMapper;
import com.limiteddrop.user.service.CustomerService;
import org.springframework.stereotype.Service;

@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {
}
