package com.echaris.api.utils;//package com.roadresq.auth.utils;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.mongodb.MongoDatabaseFactory;
//import org.springframework.data.mongodb.MongoTransactionManager;
//
//@Configuration
//public class AdminMongoConfiguration {
//    // MongoDB Atlas / a replica set is required for atomic state + activity writes.
//    @Bean
//    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
//        return new MongoTransactionManager(factory);
//    }
//}