package com.sorbonne;

import org.neo4j.driver.*;
import org.neo4j.procedure.*;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.procedure.Procedure;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.graphdb.Transaction;

public class NeuralNetworkProcedures {

    @Context
    public GraphDatabaseService db;

    // Création d'un neurone
    @Procedure(name = "nn.createNeuron", mode = Mode.WRITE)
    @Description("Créer un neurone avec un id, type, couche et fonction d'activation")
    public Stream<NeuronResult> createNeuron(
            @Name("id") String id,
            @Name("type") String type,
            @Name("layer") Long layer,
            @Name("activationFunction") String activationFunction) {

        try (Transaction tx = db.beginTx()) {
            // Créer un node représentant le neurone dans une transaction
            Node neuronNode = tx.createNode(Label.label("Neuron"));
            neuronNode.setProperty("id", id);
            neuronNode.setProperty("type", type);
            neuronNode.setProperty("layer", layer);
            neuronNode.setProperty("activationFunction", activationFunction);

            // Commit la transaction
            tx.commit();

            return Stream.of(new NeuronResult(neuronNode));
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la création du neurone", e);
        }
    }

    public static class NeuronResult {
        public Node node;

        public NeuronResult(Node node) {
            this.node = node;
        }
    }

    @Procedure(name = "nn.createRelationShipsNeuron", mode = Mode.WRITE)
    @Description("Créer des relations entre les neurones avec un poids")
    public void createRelationShipsNeuron(
            @Name("fromIds") List<String> fromIds,
            @Name("toIds") List<String> toIds,
            @Name("weight") double weight) {

        try (Transaction tx = db.beginTx()) {
            for (int i = 0; i < fromIds.size(); i++) {
                String fromId = fromIds.get(i);
                String toId = toIds.get(i);

                // Requête Cypher pour créer une relation entre deux neurones
                String cypherQuery = "MATCH (from:Neuron {id: $fromId}), (to:Neuron {id: $toId}) " +
                        "CREATE (from)-[r:CONNECTED_TO {weight: $weight}]->(to)";

                // Exécution de la requête Cypher avec les paramètres
                tx.execute(cypherQuery, Map.of(
                        "fromId", fromId,
                        "toId", toId,
                        "weight", weight
                ));
            }
            // Validation de la transaction
            tx.commit();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la création des relations entre neurones", e);
        }
    }

}