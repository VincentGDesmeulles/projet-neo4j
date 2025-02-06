package com.sorbonne;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import java.util.Map;
import java.util.stream.Stream;

public class Neo4jDatabaseManager {

    @Context
    public Log log;

    @Context
    public GraphDatabaseService db;

    @Procedure(name = "db.executeWrite", mode = Mode.WRITE)
    @Description("Exécute une requête d'écriture Cypher")
    public Stream<ExecutionResult> executeWrite(@Name("query") String query, @Name("params") Map<String, Object> params) {
        try (Transaction tx = db.beginTx()) {
            tx.executeTransactionally(query, params);
            tx.commit();
            return Stream.of(new ExecutionResult("ok"));
        } catch (Exception e) {
            log.error("Erreur lors de l'exécution de la requête", e);
            return Stream.of(new ExecutionResult("ko"));
        }
    }

    public static class ExecutionResult {
        public final String result;

        public ExecutionResult(String result) {
            this.result = result;
        }
    }
}