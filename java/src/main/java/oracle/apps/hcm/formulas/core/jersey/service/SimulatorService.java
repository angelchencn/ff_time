package oracle.apps.hcm.formulas.core.jersey.service;

import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.apps.hcm.formulas.core.jersey.parser.*;

import java.util.List;
import java.util.Map;

/**
 * Parse and run a Fast Formula with given input data.
 */
public class SimulatorService {

    public Map<String, Object> simulate(String code, Map<String, Object> inputs) {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "simulate: codeLen=" + (code == null ? 0 : code.length())
                            + " inputKeys=" + (inputs == null ? "[]" : inputs.keySet()),
                    AppsLogger.INFO);
        }

        var parseResult = FFParser.parse(code);

        if (parseResult.program() == null) {
            String error = parseResult.diagnostics().isEmpty()
                    ? "Parse error"
                    : parseResult.diagnostics().get(0).message();
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "simulate parse failure: " + error, AppsLogger.WARNING);
            }
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
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "simulate ok: outputs=" + outputData.keySet()
                                + " traceSize=" + interpreter.getTrace().size(),
                        AppsLogger.FINER);
            }
            return Map.of(
                    "status", "SUCCESS",
                    "output_data", outputData,
                    "execution_trace", interpreter.getTrace(),
                    "error", ""
            );
        } catch (Interpreter.SimulationError e) {
            // SEVERE inside catch — runtime simulation failures are useful
            // for the user (returned in the response) and useful for ops
            // (logged with stack trace).
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return Map.of(
                    "status", "ERROR",
                    "output_data", Map.of(),
                    "execution_trace", interpreter.getTrace(),
                    "error", e.getMessage()
            );
        }
    }
}
