package pl.edu.pw.thesis.orchestration.console.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pw.thesis.orchestration.console.dto.BusinessStateResponse;
import pl.edu.pw.thesis.orchestration.console.dto.RunStatusResponse;
import pl.edu.pw.thesis.orchestration.console.dto.StartRunRequest;
import pl.edu.pw.thesis.orchestration.console.dto.StartRunResponse;
import pl.edu.pw.thesis.orchestration.console.service.ConsoleRunService;

@RestController
@RequestMapping("/api/console")
public class ConsoleController {

    private final ConsoleRunService runs;

    public ConsoleController(ConsoleRunService runs) {
        this.runs = runs;
    }

    @PostMapping("/runs")
    public StartRunResponse start(@Valid @RequestBody StartRunRequest request) {
        return runs.start(request);
    }

    @GetMapping("/runs/{workflowId}")
    public RunStatusResponse status(@PathVariable String workflowId) {
        return runs.status(workflowId);
    }

    @GetMapping("/runs/{workflowId}/business")
    public BusinessStateResponse business(@PathVariable String workflowId) {
        return runs.business(workflowId);
    }
}
