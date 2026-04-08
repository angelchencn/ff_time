package oracle.apps.hcm.formulas.core.jersey.service;

import oracle.apps.hcm.formulas.core.jersey.parser.*;

import java.util.List;
import java.util.Map;

/**
 * Parse and run a Fast Formula with given input data.
 */
public class SimulatorService {

    public Map<String, Object> simulate(String code, Map<String, Object> inputs) {
        var parseResult = FFParser.parse(code);

        if (parseResult.program() == null) {
            String error = parseResult.diagnostics().isEmpty()
                    ? "Parse error"
                    : parseResult.diagnostics().get(0).message();
            return Map.of(
                    "status", "ERROR",
                    "output_data", Map.of(),
                    "execution_trace", List.of(),
                    "error", error
            );
        }

        var interpreter = new Interpreter(inputs);
        try {
            var outputData = interpreter.run(parseResult.program());
            return Map.of(
                    "status", "SUCCESS",
                    "output_data", outputData,
                    "execution_trace", interpreter.getTrace(),
                    "error", ""
            );
        } catch (Interpreter.SimulationError e) {
            return Map.of(
                    "status", "ERROR",
                    "output_data", Map.of(),
                    "execution_trace", interpreter.getTrace(),
                    "error", e.getMessage()
            );
        }
    }
}
