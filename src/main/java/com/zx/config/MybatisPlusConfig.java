package com.zx.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 配置（项目持久层统一使用 MyBatis-Plus，不使用 JPA）。
 */
@Configuration
@MapperScan(value = {
        "com.zx.auth.mapper",
        "com.zx.reservation.mapper",
        "com.zx.bookstore.catalog.mapper",
        "com.zx.bookstore.borrow.mapper",
        "com.zx.bookstore.cart.mapper",
        "com.zx.bookstore.coupon.mapper",
        "com.zx.bookstore.trade.mapper",
        "com.zx.bookstore.seckill.mapper",
        "com.zx.marketing.checkin.mapper",
        "com.zx.reader.mapper"
}, sqlSessionTemplateRef = "sqlSessionTemplate")
public class MybatisPlusConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        return factoryBean.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
