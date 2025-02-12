package com.sorbonne;

import org.neo4j.driver.*;


import java.util.List;




public class NeuralNetworkManager implements AutoCloseable {
    private final Driver driver;
    private static final String DATABASE_NAME = "projectdb";

    public NeuralNetworkManager(String uri, String username, String password) {
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

    public void createNeuron(String id, String type, int layer, String activationFunction) {
        String query = "CALL nn.createNeuron($id, $type, $layer, $activationFunction)";
        executeWrite(query, Values.parameters(
                "id", id,
                "type", type,
                "layer", layer,
                "activationFunction", activationFunction
        ));
    }

    public void createRelationshipsNeuron(List<String> fromIds, List<String> toIds, double weight) {
        String query = "CALL nn.createRelationShipsNeuron($fromIds, $toIds, $weight)";
        executeWrite(query, Values.parameters(
                "fromIds", fromIds,
                "toIds", toIds,
                "weight", weight
        ));
    }

    public static void main(String[] args) {
        NeuralNetworkManager nnManager = new NeuralNetworkManager("bolt://localhost:7687", "neo4j", "password");

        // Création de neurones
        nnManager.createNeuron("1", "input", 0, "relu");
        nnManager.createNeuron("2", "hidden", 1, "relu");
        nnManager.createNeuron("3", "output", 2, "sigmoid");

        // Création de relations entre neurones
        nnManager.createRelationshipsNeuron(List.of("1"), List.of("2"), 0.5);
        nnManager.createRelationshipsNeuron(List.of("2"), List.of("3"), 0.8);

        nnManager.close();
    }
}

