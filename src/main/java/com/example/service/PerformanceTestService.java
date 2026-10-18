package com.example.service;

import com.example.model.Auteur;

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

public class PerformanceTestService {

    private final EntityManager em;

    public PerformanceTestService(EntityManager em) {
        this.em = em;
    }

    // ------------------------------------------------------------------
    // Méthodes de chargement (réutilisées par les tests JUnit)
    // ------------------------------------------------------------------

    /** Problème N+1 : 1 requête pour les auteurs + 1 requête par auteur pour ses livres. */
    public List<Auteur> chargerAvecNPlus1() {
        TypedQuery<Auteur> query = em.createQuery("SELECT a FROM Auteur a", Auteur.class);
        List<Auteur> auteurs = query.getResultList();
        for (Auteur auteur : auteurs) {
            auteur.getLivres().size(); // déclenche le chargement LAZY
        }
        return auteurs;
    }

    /** Solution 1 : JOIN FETCH (DISTINCT évite les doublons d'auteurs). */
    public List<Auteur> chargerAvecJoinFetch() {
        TypedQuery<Auteur> query = em.createQuery(
                "SELECT DISTINCT a FROM Auteur a JOIN FETCH a.livres", Auteur.class);
        List<Auteur> auteurs = query.getResultList();
        for (Auteur auteur : auteurs) {
            auteur.getLivres().size();
        }
        return auteurs;
    }

    /** Solution 2 : Entity Graph (la requête JPQL reste inchangée). */
    public List<Auteur> chargerAvecEntityGraph() {
        EntityGraph<?> graph = em.createEntityGraph("Auteur.livres");
        TypedQuery<Auteur> query = em.createQuery(
                "SELECT DISTINCT a FROM Auteur a", Auteur.class);
        query.setHint("javax.persistence.fetchgraph", graph);
        List<Auteur> auteurs = query.getResultList();
        for (Auteur auteur : auteurs) {
            auteur.getLivres().size();
        }
        return auteurs;
    }

    /** JOIN FETCH + cache de requêtes (query cache) du cache de second niveau. */
    public List<Auteur> chargerAvecJoinFetchEtCache() {
        TypedQuery<Auteur> query = em.createQuery(
                "SELECT DISTINCT a FROM Auteur a JOIN FETCH a.livres", Auteur.class);
        query.setHint("org.hibernate.cacheable", true);
        return query.getResultList();
    }

    // ------------------------------------------------------------------
    // Méthodes de mesure (affichage console, comme dans le TP)
    // ------------------------------------------------------------------

    public void testPerformanceSansCache() {
        reinitialiser();
        long start = System.currentTimeMillis();
        chargerAvecNPlus1();
        long end = System.currentTimeMillis();
        System.out.println("Durée sans cache (N+1) : " + (end - start) + " ms");
    }

    public void testPerformanceAvecJoinFetch() {
        reinitialiser();
        long start = System.currentTimeMillis();
        chargerAvecJoinFetch();
        long end = System.currentTimeMillis();
        System.out.println("Durée avec JOIN FETCH : " + (end - start) + " ms");
    }

    public void testPerformanceAvecEntityGraph() {
        reinitialiser();
        long start = System.currentTimeMillis();
        chargerAvecEntityGraph();
        long end = System.currentTimeMillis();
        System.out.println("Durée avec Entity Graph : " + (end - start) + " ms");
    }

    public void testPerformanceAvecCache() {
        reinitialiser();

        // Première lecture : chargement depuis la base (remplit le cache)
        long start1 = System.currentTimeMillis();
        chargerAvecJoinFetchEtCache();
        long end1 = System.currentTimeMillis();
        System.out.println("Durée avec cache (lecture 1, depuis la base) : " + (end1 - start1) + " ms");

        // On vide le cache de 1er niveau pour forcer le passage par le cache de 2e niveau
        em.clear();

        // Deuxième lecture : chargement depuis le cache de second niveau
        long start2 = System.currentTimeMillis();
        chargerAvecJoinFetchEtCache();
        long end2 = System.currentTimeMillis();
        System.out.println("Durée avec cache (lecture 2, depuis le cache) : " + (end2 - start2) + " ms");
    }

    /** Vide les caches de 1er et 2e niveau pour partir d'un état « à froid ». */
    private void reinitialiser() {
        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();
    }
}
