package pl.edu.pw.thesis.orchestration.console.service;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.edu.pw.thesis.orchestration.console.dto.WorkflowStepView;

final class WorkflowHistoryMapper {

    // Temporal Java SDK registers activity types with capitalized method names
    // (reserve -> Reserve, etc.).
    private static final List<StepDef> STEPS = List.of(
            new StepDef("reserve", "Reserve inventory", "Reserve"),
            new StepDef("charge", "Charge payment", "Charge"),
            new StepDef("arrange", "Arrange shipment", "Arrange"),
            new StepDef("consume", "Consume reservation", "Consume"));

    private WorkflowHistoryMapper() {
    }

    static List<WorkflowStepView> mapSteps(List<HistoryEvent> events) {
        Map<String, String> statusByActivity = new LinkedHashMap<>();
        for (StepDef step : STEPS) {
            statusByActivity.put(step.activityName(), "PENDING");
        }

        for (HistoryEvent event : events) {
            EventType type = event.getEventType();
            if (type == EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED) {
                String name = event.getActivityTaskScheduledEventAttributes().getActivityType().getName();
                if (statusByActivity.containsKey(name)
                        && "PENDING".equals(statusByActivity.get(name))) {
                    statusByActivity.put(name, "SCHEDULED");
                }
            } else if (type == EventType.EVENT_TYPE_ACTIVITY_TASK_STARTED) {
                // started is tracked via scheduled event id; keep SCHEDULED→STARTED when known
            } else if (type == EventType.EVENT_TYPE_ACTIVITY_TASK_COMPLETED) {
                long scheduledId = event.getActivityTaskCompletedEventAttributes().getScheduledEventId();
                String name = activityNameForScheduled(events, scheduledId);
                if (name != null && statusByActivity.containsKey(name)) {
                    statusByActivity.put(name, "COMPLETED");
                }
            } else if (type == EventType.EVENT_TYPE_ACTIVITY_TASK_FAILED) {
                long scheduledId = event.getActivityTaskFailedEventAttributes().getScheduledEventId();
                String name = activityNameForScheduled(events, scheduledId);
                if (name != null && statusByActivity.containsKey(name)) {
                    statusByActivity.put(name, "FAILED");
                }
            } else if (type == EventType.EVENT_TYPE_ACTIVITY_TASK_TIMED_OUT) {
                long scheduledId = event.getActivityTaskTimedOutEventAttributes().getScheduledEventId();
                String name = activityNameForScheduled(events, scheduledId);
                if (name != null && statusByActivity.containsKey(name)) {
                    statusByActivity.put(name, "TIMED_OUT");
                }
            }
        }

        // Mark STARTED: scheduled but not completed/failed yet and a started event exists
        for (HistoryEvent event : events) {
            if (event.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_STARTED) {
                long scheduledId = event.getActivityTaskStartedEventAttributes().getScheduledEventId();
                String name = activityNameForScheduled(events, scheduledId);
                if (name != null && "SCHEDULED".equals(statusByActivity.get(name))) {
                    statusByActivity.put(name, "STARTED");
                }
            }
        }

        List<WorkflowStepView> views = new ArrayList<>();
        for (StepDef step : STEPS) {
            views.add(new WorkflowStepView(
                    step.id(),
                    step.label(),
                    step.activityName(),
                    statusByActivity.get(step.activityName())));
        }
        return views;
    }

    private static String activityNameForScheduled(List<HistoryEvent> events, long scheduledEventId) {
        for (HistoryEvent event : events) {
            if (event.getEventId() == scheduledEventId
                    && event.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED) {
                return event.getActivityTaskScheduledEventAttributes().getActivityType().getName();
            }
        }
        return null;
    }

    private record StepDef(String id, String label, String activityName) {
    }
}
