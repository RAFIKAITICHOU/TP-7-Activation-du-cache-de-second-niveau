package com.example.model;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "auteurs")
@NamedEntityGraph(
        name = "Auteur.livres",
        attributeNodes = @NamedAttributeNode("livres")
)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Auteur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;

    @OneToMany(mappedBy = "auteur", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE) // cache de la collection
    private List<Livre> livres = new ArrayList<>();

    public Auteur() {
    }

    public Auteur(String nom) {
        this.nom = nom;
    }

    public void ajouterLivre(Livre livre) {
        livres.add(livre);
        livre.setAuteur(this);
    }

    public void retirerLivre(Livre livre) {
        livres.remove(livre);
        livre.setAuteur(null);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public List<Livre> getLivres() { return livres; }
    public void setLivres(List<Livre> livres) { this.livres = livres; }
}
