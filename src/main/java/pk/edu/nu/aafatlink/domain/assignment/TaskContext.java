package pk.edu.nu.aafatlink.domain.assignment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Aggregate read model used by UC06, UC09 and UC10 screens. */
public record TaskContext(
        AssignmentContext assignmentContext,
        List<ProgressUpdate> progressHistory,
        TaskCompletion completion,
        List<CompletionEvidence> completionEvidence,
        ClosureReview latestClosureReview) {
    public TaskContext {
        Objects.requireNonNull(assignmentContext, "assignmentContext");
        progressHistory = List.copyOf(Objects.requireNonNull(progressHistory, "progressHistory"));
        completionEvidence = List.copyOf(Objects.requireNonNull(completionEvidence, "completionEvidence"));
    }

    public Optional<TaskCompletion> completionDetails() {
        return Optional.ofNullable(completion);
    }

    public Optional<ClosureReview> latestReview() {
        return Optional.ofNullable(latestClosureReview);
    }
}
