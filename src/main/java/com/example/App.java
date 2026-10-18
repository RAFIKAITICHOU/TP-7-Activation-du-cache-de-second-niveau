package com.example;

import com.example.service.DataInitService;
import com.example.service.PerformanceTestService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class App {

    public static void main(String[] args) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("hibernate-cache-pu");
        EntityManager em = emf.createEntityManager();

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        new DataInitService(em).initData();
        PerformanceTestService service = new PerformanceTestService(em);

        System.out.println("\n=== Test sans optimisation (problème N+1) ===");
        stats.clear();
        service.testPerformanceSansCache();
        System.out.println("Requêtes SQL : " + stats.getPrepareStatementCount());

        System.out.println("\n=== Test avec JOIN FETCH ===");
        stats.clear();
        service.testPerformanceAvecJoinFetch();
        System.out.println("Requêtes SQL : " + stats.getPrepareStatementCount());

        System.out.println("\n=== Test avec Entity Graph ===");
        stats.clear();
        service.testPerformanceAvecEntityGraph();
        System.out.println("Requêtes SQL : " + stats.getPrepareStatementCount());

        System.out.println("\n=== Test avec cache de second niveau ===");
        stats.clear();
        service.testPerformanceAvecCache();
        System.out.println("Requêtes SQL : " + stats.getPrepareStatementCount());
        System.out.println("Cache L2 - hits : " + stats.getSecondLevelCacheHitCount()
                + ", misses : " + stats.getSecondLevelCacheMissCount()
                + ", puts : " + stats.getSecondLevelCachePutCount());
        System.out.println("Query cache - hits : " + stats.getQueryCacheHitCount()
                + ", misses : " + stats.getQueryCacheMissCount());

        em.close();
        emf.close();
    }
}
