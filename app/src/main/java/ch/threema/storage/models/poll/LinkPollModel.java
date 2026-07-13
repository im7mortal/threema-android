package ch.threema.storage.models.poll;

public interface LinkPollModel {
    public enum Type {
        CONTACT, GROUP
    }

    int getPollId();

    Type getType();
}
