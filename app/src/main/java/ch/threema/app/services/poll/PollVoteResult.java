package ch.threema.app.services.poll;


public class PollVoteResult {
    private final boolean success;

    public PollVoteResult(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
