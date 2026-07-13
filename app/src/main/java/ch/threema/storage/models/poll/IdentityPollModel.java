package ch.threema.storage.models.poll;

public class IdentityPollModel implements LinkPollModel {
    public static final String TABLE = "identity_ballot";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_IDENTITY = "identity";
    public static final String COLUMN_POLL_ID = "ballotId";

    private int id;
    private String identity;
    private int pollId;

    public int getId() {
        return this.id;
    }

    public IdentityPollModel setId(int id) {
        this.id = id;
        return this;
    }

    public String getIdentity() {
        return identity;
    }

    public IdentityPollModel setIdentity(String identity) {
        this.identity = identity;
        return this;
    }

    public int getPollId() {
        return pollId;
    }

    @Override
    public Type getType() {
        return Type.CONTACT;
    }

    public IdentityPollModel setPollId(int pollId) {
        this.pollId = pollId;
        return this;
    }
}
