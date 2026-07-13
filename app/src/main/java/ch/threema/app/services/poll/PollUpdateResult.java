package ch.threema.app.services.poll;


import ch.threema.storage.models.poll.PollModel;

public class PollUpdateResult extends PollResult {
    public enum Operation {
        CREATE,
        UPDATE,
        CLOSE
    }

    private final PollModel pollModel;
    private final Operation operation;

    public PollUpdateResult(PollModel pollModel, Operation operation) {
        this.pollModel = pollModel;
        this.operation = operation;
    }

    public PollModel getPollModel() {
        return pollModel;
    }

    public Operation getOperation() {
        return operation;
    }
}
