package com.example.service;

import com.example.model.Auteur;
import com.example.model.Livre;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

/**
 * Insère 10 auteurs ayant chacun 5 livres.
 * La transaction est gérée ici (RESOURCE_LOCAL, hors serveur d'applications).
 */
public class DataInitService {

    private final EntityManager em;

    public DataInitService(EntityManager em) {
        this.em = em;
    }

    public void initData() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            for (int i = 1; i <= 10; i++) {
                Auteur auteur = new Auteur("Auteur " + i);
                for (int j = 1; j <= 5; j++) {
                    auteur.ajouterLivre(new Livre("Livre " + j + " de l'auteur " + i));
                }
                em.persist(auteur);
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }
}
