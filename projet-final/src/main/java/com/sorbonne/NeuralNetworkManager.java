package com.sorbonne;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import java.util.Map;
import java.util.stream.Stream;

public class NeuralNetworkManager {

    @Context
    public Log log;

    @Context
    public GraphDatabaseService db;

    @Procedure(name = "nn.createNetwork", mode = Mode.WRITE)
    @Description("Crée un réseau de neurones")
    public Stream<ExecutionResult> createNetwork(@Name("structure") Map<String, Object> structure) {
        try (Transaction tx = db.beginTx()) {
            for (Map.Entry<String, Object> entry : structure.entrySet()) {
                tx.executeTransactionally("CREATE (n:Neuron {id: $id, layer: $layer, type: $type, activation_function: $activation_function, output: $output, weight: $weight})",
                        Map.of(
                                "id", entry.getKey(),
                                "layer", structure.get("layer"),
                                "type", structure.get("type"),
                                "activation_function", structure.get("activation_function"),
                                "output", structure.getOrDefault("output", 0.0),
                                "weight", structure.getOrDefault("weight", 1.0)
                        ));
            }
            tx.commit();
            return Stream.of(new ExecutionResult("network_created"));
        } catch (Exception e) {
            log.error("Erreur lors de la création du réseau", e);
            return Stream.of(new ExecutionResult("error"));
        }
    }

    @Procedure(name = "nn.forwardPass", mode = Mode.WRITE)
    @Description("Exécute un passage avant pour le réseau de neurones")
    public Stream<ExecutionResult> forwardPass() {
        try (Transaction tx = db.beginTx()) {
            tx.executeTransactionally("MATCH (n:Neuron {type: 'input'})-[:CONNECTED_TO]->(m:Neuron {type: 'hidden'}) SET m.output = n.output * m.weight");
            tx.executeTransactionally("MATCH (m:Neuron {type: 'hidden'})-[:CONNECTED_TO]->(o:Neuron {type: 'output'}) SET o.output = m.output * o.weight");
            tx.commit();
            return Stream.of(new ExecutionResult("forward_pass_completed"));
        } catch (Exception e) {
            log.error("Erreur lors du passage avant", e);
            return Stream.of(new ExecutionResult("error"));
        }
    }

    @Procedure(name = "nn.backwardPass", mode = Mode.WRITE)
    @Description("Exécute un passage arrière pour l'apprentissage")
    public Stream<ExecutionResult> backwardPass(@Name("learningRate") double learningRate) {
        try (Transaction tx = db.beginTx()) {
            tx.executeTransactionally("MATCH (o:Neuron {type: 'output'})<-[:CONNECTED_TO]-(h:Neuron {type: 'hidden'}) SET h.weight = h.weight - (o.output * $learningRate)", Map.of("learningRate", learningRate));
            tx.executeTransactionally("MATCH (h:Neuron {type: 'hidden'})<-[:CONNECTED_TO]-(i:Neuron {type: 'input'}) SET i.weight = i.weight - (h.output * $learningRate)", Map.of("learningRate", learningRate));
            tx.commit();
            return Stream.of(new ExecutionResult("backward_pass_completed"));
        } catch (Exception e) {
            log.error("Erreur lors du passage arrière", e);
            return Stream.of(new ExecutionResult("error"));
        }
    }

    public static class ExecutionResult {
        public final String result;

        public ExecutionResult(String result) {
            this.result = result;
        }
    }
}