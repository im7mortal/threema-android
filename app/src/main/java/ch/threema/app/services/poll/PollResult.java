package ch.threema.app.services.poll;


import java.util.ArrayList;
import java.util.List;

abstract class PollResult {
    private List<Integer> messages = new ArrayList<Integer>();
    private boolean success = false;

    public boolean isSuccess() {
        return this.success;
    }

    public List<Integer> getMessageResources() {
        return this.messages;
    }

    protected PollResult error(int messageResourceId) {
        this.success = false;
        this.messages.add(messageResourceId);
        return this;
    }

    public PollResult success() {
        this.success = true;
        return this;
    }

}
