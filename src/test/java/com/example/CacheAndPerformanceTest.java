package com.example;

import com.example.model.Auteur;
import com.example.service.DataInitService;
import com.example.service.PerformanceTestService;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vérifie, grâce aux statistiques Hibernate, que :
 *  - le problème N+1 existe sans optimisation (11 requêtes),
 *  - JOIN FETCH et Entity Graph le résolvent (1 requête),
 *  - le cache de second niveau évite les accès à la base.
 */
class CacheAndPerformanceTest {

    private static EntityManagerFactory emf;
    private static Statistics stats;

    private EntityManager em;
    private PerformanceTestService service;

    @BeforeAll
    static void initEmf() {
        emf = Persistence.createEntityManagerFactory("hibernate-cache-pu");
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        EntityManager initEm = emf.createEntityManager();
        new DataInitService(initEm).initData();
        initEm.close();
    }

    @AfterAll
    static void closeEmf() {
        emf.close();
    }

    @BeforeEach
    void setUp() {
        emf.getCache().evictAll(); // caches L2 et query cache à froid
        stats.clear();
        em = emf.createEntityManager();
        service = new PerformanceTestService(em);
    }

    @AfterEach
    void tearDown() {
        em.close();
    }

    @Test
    void donneesInitialesCorrectes() {
        Long auteurs = em.createQuery("SELECT COUNT(a) FROM Auteur a", Long.class).getSingleResult();
        Long livres = em.createQuery("SELECT COUNT(l) FROM Livre l", Long.class).getSingleResult();
        assertEquals(10L, auteurs);
        assertEquals(50L, livres);
    }

    @Test
    void problemeNPlus1_genere11Requetes() {
        stats.clear();
        List<Auteur> auteurs = service.chargerAvecNPlus1();

        assertEquals(10, auteurs.size());
        // 1 requête pour les auteurs + 10 requêtes (une par collection de livres)
        assertEquals(11, stats.getPrepareStatementCount());
    }

    @Test
    void joinFetch_genere1SeuleRequete() {
        stats.clear();
        List<Auteur> auteurs = service.chargerAvecJoinFetch();

        assertEquals(10, auteurs.size()); // DISTINCT : pas de doublons
        assertEquals(1, stats.getPrepareStatementCount());
        auteurs.forEach(a -> assertEquals(5, a.getLivres().size()));
    }

    @Test
    void entityGraph_genere1SeuleRequete() {
        stats.clear();
        List<Auteur> auteurs = service.chargerAvecEntityGraph();

        assertEquals(10, auteurs.size());
        assertEquals(1, stats.getPrepareStatementCount());
        auteurs.forEach(a -> assertTrue(Hibernate.isInitialized(a.getLivres())));
    }

    @Test
    void cacheEntite_deuxiemeLectureDepuisLeCache() {
        Long id = em.createQuery("SELECT MIN(a.id) FROM Auteur a", Long.class).getSingleResult();
        em.clear();
        emf.getCache().evictAll();
        stats.clear();

        // 1ère lecture : depuis la base
        Auteur a1 = em.find(Auteur.class, id);
        assertNotNull(a1);
        assertEquals(1, stats.getPrepareStatementCount());
        assertEquals(0, stats.getSecondLevelCacheHitCount());
        em.close();

        // 2e lecture dans une NOUVELLE session : depuis le cache de second niveau
        em = emf.createEntityManager();
        Auteur a2 = em.find(Auteur.class, id);
        assertNotNull(a2);
        assertEquals(1, stats.getPrepareStatementCount(), "aucune requête SQL supplémentaire");
        assertEquals(1, stats.getSecondLevelCacheHitCount());
        assertTrue(emf.getCache().contains(Auteur.class, id));
    }

    @Test
    void cacheDeRequete_deuxiemeLectureSansSql() {
        service.chargerAvecJoinFetchEtCache(); // remplit query cache + cache d'entités
        em.clear();
        stats.clear();

        List<Auteur> auteurs = service.chargerAvecJoinFetchEtCache();

        assertEquals(10, auteurs.size());
        assertEquals(1, stats.getQueryCacheHitCount());
        assertEquals(0, stats.getPrepareStatementCount(), "tout doit venir du cache");
    }

    @Test
    void cacheDeCollection_livresChargesSansSql() {
        service.chargerAvecJoinFetch(); // remplit les caches d'entités et de collections
        em.clear();
        stats.clear();

        Long id = em.createQuery("SELECT MIN(a.id) FROM Auteur a", Long.class).getSingleResult();
        long avant = stats.getPrepareStatementCount(); // la requête MIN est du SQL

        Auteur auteur = em.find(Auteur.class, id);
        assertEquals(5, auteur.getLivres().size());

        assertEquals(avant, stats.getPrepareStatementCount(),
                "auteur + livres doivent venir du cache de second niveau");
        assertTrue(stats.getSecondLevelCacheHitCount() > 0);
    }
}
