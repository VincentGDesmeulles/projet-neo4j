package com.sorbonne;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import java.util.Map;
import java.util.stream.Stream;

public class Neo4jDatabaseManager implements AutoCloseable {
    private final Driver driver;
    private static final String DATABASE_NAME = "projectdb";

    public Neo4jDatabaseManager(String uri, String username, String password) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }

    @Override
    public void close() {
        driver.close();
    }

    public void executeWrite(String cypherQuery, Value parameters) {
        try (Session session = driver.session(SessionConfig.forDatabase(DATABASE_NAME))) {
            session.writeTransaction(tx -> {
                tx.run(cypherQuery, parameters);
                return null;
            });
        }
    }

    public Result executeRead(String cypherQuery) {
        try (Session session = driver.session(SessionConfig.forDatabase(DATABASE_NAME))) {
            return session.readTransaction(tx -> tx.run(cypherQuery));
        }
    }

    public static void main(String[] args) {
        // Création d'une instance de la base de données
        Neo4jDatabaseManager dbManager = new Neo4jDatabaseManager("bolt://localhost:7687", "neo4j", "password");

        // Requête de test pour vérifier la connexion
        String testQuery = "RETURN 'Connexion réussie à Neo4j' AS message";

        try {
            Result result = dbManager.executeRead(testQuery);
            while (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                System.out.println(record.get("message").asString());
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la connexion à Neo4j : " + e.getMessage());
        } finally {
            dbManager.close();
        }
    }
}
