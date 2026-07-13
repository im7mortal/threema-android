package ch.threema.app.services.poll;


public class PollPublishResult extends PollResult {
    @Override
    protected PollPublishResult error(int messageResourceId) {
        super.error(messageResourceId);
        return this;
    }
}
