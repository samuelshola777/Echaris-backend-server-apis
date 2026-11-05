package com.echaris.api.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoDbCleaner {

    @Autowired
    private MongoTemplate mongoTemplate;

//    @PostConstruct
    public void dropAllCollections() {

        System.out.println("Deleting all collections in the database...");
        for (String collectionName : mongoTemplate.getCollectionNames()) {

            System.out.println("Deleting collection: " + collectionName);
            mongoTemplate.dropCollection(collectionName);
        }
    }
}
