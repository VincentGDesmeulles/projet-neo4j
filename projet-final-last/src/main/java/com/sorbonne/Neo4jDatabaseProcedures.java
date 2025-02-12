package com.sorbonne;

import org.neo4j.procedure.Procedure;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import java.util.Map;
import java.util.stream.Stream;

public class Neo4jDatabaseProcedures {

    // Instance unique du Driver Neo4j
    private static final Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "password"));

    // Constructeur public sans argument
    public Neo4jDatabaseProcedures() {
        // Constructeur sans logique spécifique
    }

    /**
     * Méthode pour exécuter une requête Cypher de type écriture.
     *
     * @param cypherQuery La requête Cypher à exécuter.
     * @param parameters  Les paramètres de la requête.
     */
    @Procedure(name = "db.executeWrite", mode = Mode.WRITE)
    @Description("Exécuter une requête Cypher de type écriture")
    public void executeWrite(
            @Name("cypherQuery") String cypherQuery,
            @Name("parameters") Map<String, Object> parameters) {

        // Exécution de la requête d'écriture dans Neo4j
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> tx.run(cypherQuery, parameters));
        }
    }

    /**
     * Méthode pour exécuter une requête Cypher de type lecture.
     *
     * @param cypherQuery La requête Cypher à exécuter.
     * @return Un flux de chaînes représentant les résultats de la requête.
     */
    @Procedure(name = "db.executeRead", mode = Mode.READ)
    @Description("Exécuter une requête Cypher de type lecture")
    public Stream<String> executeRead(@Name("cypherQuery") String cypherQuery) {

        // Exécution d'une requête de lecture et retour des résultats
        try (Session session = driver.session()) {
            Result result = session.readTransaction(tx -> tx.run(cypherQuery));
            // On transforme les résultats en un flux de chaînes
            return result.stream()
                    .map(record -> record.get("message").asString());
        }
    }

    /**
     * Méthode pour fermer le Driver Neo4j.
     * À appeler lors de l'arrêt de l'application pour libérer les ressources.
     */
    public static void closeDriver() {
        driver.close();
    }
}