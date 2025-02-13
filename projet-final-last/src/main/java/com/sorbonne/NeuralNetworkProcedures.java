package com.sorbonne;

import org.neo4j.procedure.*;
import org.neo4j.graphdb.*;

import java.util.*;
import java.util.stream.Stream;

public class NeuralNetworkProcedures {

    @Context
    public GraphDatabaseService db;

    @Procedure(name = "nn.createNeuron", mode = Mode.WRITE)
    public void createNeuron(
            @Name("id") String id,
            @Name("type") String type,
            @Name("layer") long layer,
            @Name("activationFunction") String activationFunction) {

        try (Transaction tx = db.beginTx()) {
            Map<String, Object> params = new HashMap<>();
            params.put("id", id);
            params.put("type", type);
            params.put("layer", layer);
            params.put("activationFunction", activationFunction);

            tx.execute("""
                CREATE (n:Neuron {
                    id: $id,
                    type: $type,
                    layer: $layer,
                    bias: 0.0,
                    output: null,
                    m_bias: 0.0,
                    v_bias: 0.0,
                    activation_function: $activationFunction
                })
            """, params);

            tx.commit();
        }
    }

    @Procedure(name = "nn.createConnection", mode = Mode.WRITE)
    public void createConnection(
            @Name("fromId") String fromId,
            @Name("toId") String toId,
            @Name("weight") double weight) {

        try (Transaction tx = db.beginTx()) {
            Map<String, Object> params = new HashMap<>();
            params.put("fromId", fromId);
            params.put("toId", toId);
            params.put("weight", weight);

            tx.execute("""
                MATCH (n1:Neuron {id: $fromId})
                MATCH (n2:Neuron {id: $toId})
                CREATE (n1)-[:CONNECTED_TO {weight: $weight}]->(n2)
            """, params);

            tx.commit();
        }
    }

    @Procedure(name = "nn.forwardPass", mode = Mode.WRITE)
    public void forwardPass() {
        try (Transaction tx = db.beginTx()) {
            tx.execute("""
                MATCH (row_for_inputs:Row {type: 'inputsRow'})-[inputsValue_R:CONTAINS]->(input:Neuron {type: 'input'})
                MATCH (input)-[r1:CONNECTED_TO]->(hidden:Neuron {type: 'hidden'})
                MATCH (hidden)-[r2:CONNECTED_TO]->(output:Neuron {type: 'output'})
                MATCH (output)-[outputsValues_R:CONTAINS]->(row_for_outputs:Row {type: 'outputsRow'})
                WITH DISTINCT row_for_inputs, inputsValue_R, input, r1, hidden, r2, output, outputsValues_R, row_for_outputs,
                SUM(COALESCE(outputsValues_R.output, 0) * r1.weight) AS weighted_sum
                SET hidden.output = CASE 
                    WHEN hidden.activation_function = 'relu' THEN CASE WHEN (weighted_sum + hidden.bias) > 0 THEN (weighted_sum + hidden.bias) ELSE 0 END
                    WHEN hidden.activation_function = 'sigmoid' THEN 1 / (1 + EXP(-(weighted_sum + hidden.bias)))
                    WHEN hidden.activation_function = 'tanh' THEN (EXP(2 * (weighted_sum + hidden.bias)) - 1) / (EXP(2 * (weighted_sum + hidden.bias)) + 1)
                    ELSE weighted_sum + hidden.bias
                END
            """);

            tx.commit();
        }
    }

    @Procedure(name = "nn.backwardPassAdam", mode = Mode.WRITE)
    public void backwardPassAdam(
            @Name("learningRate") double learningRate,
            @Name("beta1") double beta1,
            @Name("beta2") double beta2,
            @Name("epsilon") double epsilon,
            @Name("t") long t) {

        try (Transaction tx = db.beginTx()) {
            Map<String, Object> params = new HashMap<>();
            params.put("learningRate", learningRate);
            params.put("beta1", beta1);
            params.put("beta2", beta2);
            params.put("epsilon", epsilon);
            params.put("t", t);

            tx.execute("""
                MATCH (output:Neuron {type: 'output'})<-[r:CONNECTED_TO]-(prev:Neuron)
                MATCH (output)-[outputsValues_R:CONTAINS]->(row_for_outputs:Row {type: 'outputsRow'})
                WITH DISTINCT output, r, prev, outputsValues_R, row_for_outputs,
                CASE 
                    WHEN output.activation_function = 'softmax' THEN outputsValues_R.output - outputsValues_R.expected_output
                    WHEN output.activation_function = 'sigmoid' THEN (outputsValues_R.output - outputsValues_R.expected_output) * outputsValues_R.output * (1 - outputsValues_R.output)
                    WHEN output.activation_function = 'tanh' THEN (outputsValues_R.output - outputsValues_R.expected_output) * (1 - outputsValues_R.output^2)
                    ELSE outputsValues_R.output - outputsValues_R.expected_output
                END AS gradient,
                $t AS t
                SET r.m = $beta1 * COALESCE(r.m, 0) + (1 - $beta1) * gradient * COALESCE(prev.output, 0)
                SET r.v = $beta2 * COALESCE(r.v, 0) + (1 - $beta2) * (gradient * COALESCE(prev.output, 0))^2
                SET r.weight = r.weight - $learningRate * (r.m / (1 - ($beta1 ^ t))) / (SQRT(r.v / (1 - ($beta2 ^ t))) + $epsilon)
            """, params);

            tx.commit();
        }
    }

    @Procedure(name = "nn.setInputs", mode = Mode.WRITE)
    public void setInputs(
            @Name("rowId") String rowId,
            @Name("inputFeatureId") String inputFeatureId,
            @Name("value") double value) {

        try (Transaction tx = db.beginTx()) {
            Map<String, Object> params = new HashMap<>();
            params.put("rowId", rowId);
            params.put("inputFeatureId", inputFeatureId);
            params.put("value", value);

            tx.execute("""
            MATCH (row:Row {type: 'inputsRow', id: $rowId})-[r:CONTAINS {id: $inputFeatureId}]->(input:Neuron {type: 'input'})
            SET r.output = $value
        """, params);

            tx.commit();
        }
    }
    @Procedure(name = "nn.setExpectedOutputs", mode = Mode.WRITE)
    public void setExpectedOutputs(
            @Name("rowId") String rowId,
            @Name("outputFeatureId") String outputFeatureId,
            @Name("value") double value) {

        try (Transaction tx = db.beginTx()) {
            Map<String, Object> params = new HashMap<>();
            params.put("rowId", rowId);
            params.put("outputFeatureId", outputFeatureId);
            params.put("value", value);

            tx.execute("""
            MATCH (output:Neuron {type: 'output'})-[r:CONTAINS {id: $outputFeatureId}]->(row:Row {type: 'outputsRow', id: $rowId})
            SET r.expected_output = $value
        """, params);

            tx.commit();
        }
    }
    @Procedure(name = "nn.computeLoss", mode = Mode.READ)
    public Stream<LossResult> computeLoss(@Name("taskType") String taskType) {

        try (Transaction tx = db.beginTx()) {
            String query;
            if (taskType.equals("classification")) {
                query = """
                MATCH (output:Neuron {type: 'output'})-[outputsValues_R:CONTAINS]->(row_for_outputs:Row {type: 'outputsRow'})
                WITH outputsValues_R,
                     COALESCE(outputsValues_R.output, 0) AS predicted,
                     COALESCE(outputsValues_R.expected_output, 0) AS actual,
                     1e-10 AS epsilon
                RETURN SUM(-actual * LOG(predicted + epsilon) - (1 - actual) * LOG(1 - predicted + epsilon)) AS loss
            """;
            } else if (taskType.equals("regression")) {
                query = """
                MATCH (output:Neuron {type: 'output'})-[outputsValues_R:CONTAINS]->(row_for_outputs:Row {type: 'outputsRow'})
                WITH outputsValues_R,
                     COALESCE(outputsValues_R.output, 0) AS predicted,
                     COALESCE(outputsValues_R.expected_output, 0) AS actual
                RETURN AVG((predicted - actual)^2) AS loss
            """;
            } else {
                throw new IllegalArgumentException("Invalid task type. Use 'classification' or 'regression'.");
            }

            Result result = tx.execute(query);
            double loss = (double) result.next().get("loss");
            return Stream.of(new LossResult(loss));
        }
    }

    public class LossResult {
        public double loss;

        public LossResult(double loss) {
            this.loss = loss;
        }
    }
    @Procedure(name = "nn.evaluateModel", mode = Mode.READ)
    public Stream<EvaluationResult> evaluateModel() {

        try (Transaction tx = db.beginTx()) {
            Result result = tx.execute("""
            MATCH (output:Neuron {type: 'output'})-[outputsValues_R:CONTAINS]->(row_for_outputs:Row {type: 'outputsRow'})
            RETURN output.id AS id, outputsValues_R.output AS predicted, outputsValues_R.expected_output AS expected
        """);

            List<EvaluationResult> evaluationResults = new ArrayList<>();
            while (result.hasNext()) {
                Map<String, Object> record = result.next();
                String id = (String) record.get("id");
                double predicted = (double) record.get("predicted");
                double expected = (double) record.get("expected");
                evaluationResults.add(new EvaluationResult(id, predicted, expected));
            }

            return evaluationResults.stream();
        }
    }

    public class EvaluationResult {
        public String id;
        public double predicted;
        public double expected;

        public EvaluationResult(String id, double predicted, double expected) {
            this.id = id;
            this.predicted = predicted;
            this.expected = expected;
        }
    }

}

